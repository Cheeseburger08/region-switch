/* Per-app country hooks. Root, current boot only; no network listener. */
#include "frida-core.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <dirent.h>
#include <pthread.h>

#define BASE "/data/adb/uk-hook-switch"
#define MAX_TARGETS 64
typedef struct {
  char package[200];
  char country[3], iso3[4], operator[7];
  guint pid;
  FridaSession *session;
  FridaScript *script;
  gboolean ready, busy;
  unsigned calls, attachments;
  gboolean failed;
} Target;
static Target targets[MAX_TARGETS];
static FridaDevice *device;
static GMainLoop *loop;
static volatile sig_atomic_t stopping;
static gboolean gating;
static char boot[64], last_config[14000];
static const char *phase="starting";
static gchar *region_source;
static unsigned pending_detaches, pending_resumes;
static gboolean shutdown_started, gate_closed;
static void finish_shutdown(void) {
  if(shutdown_started&&gate_closed&&!pending_detaches&&!pending_resumes)g_main_loop_quit(loop);
}

static void status(void) {
  FILE *f=fopen(BASE "/status.tmp","w"); if(!f)return;
  fprintf(f,"{\"controller_pid\":%d,\"boot_id\":\"%s\",\"phase\":\"%s\",\"targets\":[",getpid(),boot,phase);
  int comma=0;
  for(int i=0;i<MAX_TARGETS;i++) {
    Target *t=&targets[i]; if(!t->package[0])continue;
    fprintf(f,"%s{\"package\":\"%s\",\"country\":\"%s\",\"operator\":\"%s\",\"pid\":%u,\"ready\":%s,\"error\":%s,\"calls\":%u,\"attachments\":%u}",comma++?",":"",t->package,t->country,t->operator,t->pid,t->ready?"true":"false",t->failed?"true":"false",t->calls,t->attachments);
  }
  fprintf(f,"]}\n");fclose(f);rename(BASE "/status.tmp",BASE "/status.json");
}
static void error_log(const char *where,GError **error) {
  if(*error){fprintf(stderr,"%s: %s\n",where,(*error)->message);fflush(stderr);g_clear_error(error);}
}
static void on_message(FridaScript *script,const gchar *message,GBytes *data,gpointer user) {
  Target *t=user;
  if(strstr(message,"hooks_ready")){t->ready=TRUE;t->failed=FALSE;status();}
  if(strstr(message,"country_override")||strstr(message,"cached_region_override")){t->calls++;status();}
  if(strstr(message,"\"type\":\"error\"")){t->failed=TRUE;status();}
}
static void on_detached(FridaSession *session,FridaSessionDetachReason reason,FridaCrash *crash,gpointer user) {
  Target *t=user;if(session!=t->session)return;
  t->ready=FALSE;t->pid=0;
  if(t->script){g_object_unref(t->script);t->script=NULL;}
  g_object_unref(t->session);t->session=NULL;status();
}
typedef struct {FridaSession *session;FridaScript *script;} DetachJob;
static void detached_done(GObject *source,GAsyncResult *result,gpointer data) {
  DetachJob *job=data;GError *e=NULL;
  frida_session_detach_finish(job->session,result,&e);error_log("detach",&e);
  if(job->script)g_object_unref(job->script);g_object_unref(job->session);g_free(job);
  pending_detaches--;finish_shutdown();
}
static void detach(Target *t) {
  if(t->session){
    DetachJob *job=g_new0(DetachJob,1);job->session=t->session;job->script=t->script;
    g_signal_handlers_disconnect_by_data(t->session,t);
    if(t->script)g_signal_handlers_disconnect_by_data(t->script,t);
    t->session=NULL;t->script=NULL;pending_detaches++;
    frida_session_detach(job->session,NULL,detached_done,job);
  }else if(t->script){g_signal_handlers_disconnect_by_data(t->script,t);g_object_unref(t->script);t->script=NULL;}
  t->pid=0;t->ready=FALSE;t->busy=FALSE;
}
static gboolean valid_package(const char *s) {
  if(!s[0]||strlen(s)>180||!strchr(s,'.'))return FALSE;
  for(const char *p=s;*p;p++)if(!g_ascii_isalnum(*p)&&*p!='.'&&*p!='_')return FALSE;
  return strcmp(s,"com.android.systemui")&&strcmp(s,"com.android.phone")&&strcmp(s,"local.hessam.ukhooks");
}
static gboolean parse_profile(const char *line,Target *t) {
  gchar **fields=g_strsplit(line,"\t",-1);guint n=g_strv_length(fields);gboolean ok=FALSE;
  if((n!=1&&n!=4)||!valid_package(fields[0]))goto out;
  const char *country=n==1?"gb":fields[1],*iso3=n==1?"GBR":fields[2],*operator=n==1?"23415":fields[3];
  if(strlen(country)!=2||strlen(iso3)!=3||strlen(operator)<5||strlen(operator)>6)goto out;
  for(int i=0;i<2;i++)if(!g_ascii_islower(country[i]))goto out;
  for(int i=0;i<3;i++)if(!g_ascii_isupper(iso3[i]))goto out;
  for(const char *p=operator;*p;p++)if(!g_ascii_isdigit(*p))goto out;
  g_strlcpy(t->package,fields[0],sizeof(t->package));g_strlcpy(t->country,country,sizeof(t->country));
  g_strlcpy(t->iso3,iso3,sizeof(t->iso3));g_strlcpy(t->operator,operator,sizeof(t->operator));ok=TRUE;
out:g_strfreev(fields);return ok;
}
static void reload_config(void) {
  for(int i=0;i<MAX_TARGETS;i++)if(targets[i].busy)return;
  gchar *raw=NULL;gsize length=0;
  if(!g_file_get_contents(BASE "/targets.conf",&raw,&length,NULL))return;
  if(length>=sizeof(last_config)||!strcmp(last_config,raw)){g_free(raw);return;}
  gchar **lines=g_strsplit(raw,"\n",-1);int count=0;gboolean valid=TRUE;Target next[MAX_TARGETS]={0};
  for(int i=0;lines[i];i++)if(lines[i][0]){
    if(count==MAX_TARGETS||!parse_profile(lines[i],&next[count])){valid=FALSE;break;}
    for(int j=0;j<count;j++)if(!strcmp(next[j].package,next[count].package))valid=FALSE;
    count++;
  }
  if(!valid){g_strfreev(lines);g_free(raw);return;}
  for(int i=0;i<MAX_TARGETS;i++)if(targets[i].package[0]){
    gboolean keep=FALSE;for(int j=0;j<count;j++)if(!strcmp(next[j].package,targets[i].package)&&!strcmp(next[j].country,targets[i].country)&&!strcmp(next[j].iso3,targets[i].iso3)&&!strcmp(next[j].operator,targets[i].operator))keep=TRUE;
    if(!keep){detach(&targets[i]);memset(&targets[i],0,sizeof(Target));}
  }
  for(int j=0;j<count;j++){
    gboolean exists=FALSE;for(int i=0;i<MAX_TARGETS;i++)if(!strcmp(next[j].package,targets[i].package))exists=TRUE;
    if(!exists)for(int i=0;i<MAX_TARGETS;i++)if(!targets[i].package[0]){targets[i]=next[j];break;}
  }
  g_strlcpy(last_config,raw,sizeof(last_config));g_strfreev(lines);g_free(raw);status();
}
/* Label only our own anonymous agent file. No broad SELinux policy changes. */
static void *label_agent(void *unused) {
  const char *label="u:object_r:ksu_file:s0";
  while(!stopping){
    DIR *d=opendir("/proc/self/fd");struct dirent *e;
    if(d){while((e=readdir(d))){
      char path[128],link[256],current[128];
      snprintf(path,sizeof(path),"/proc/self/fd/%s",e->d_name);
      ssize_t n=readlink(path,link,sizeof(link)-1);if(n<0)continue;link[n]=0;
      if(strcmp(link,"/memfd:frida-agent-64.so (deleted)"))continue;
      ssize_t c=getxattr(path,"security.selinux",current,sizeof(current)-1);
      if(c>0){current[c]=0;if(!strcmp(current,label))continue;}
      setxattr(path,"security.selinux",label,strlen(label)+1,0);
    }closedir(d);}usleep(50000);
  }return NULL;
}
static void attach_target(Target *t,guint pid) {
  GError *e=NULL;if(t->pid==pid||t->busy)return;
  if(t->session)detach(t);
  t->busy=TRUE;t->pid=pid;t->ready=FALSE;t->failed=FALSE;
  t->session=frida_device_attach_sync(device,pid,NULL,NULL,&e);if(e)goto fail;
  g_signal_connect(t->session,"detached",G_CALLBACK(on_detached),t);
  t->script=frida_session_create_script_sync(t->session,region_source,NULL,NULL,&e);if(e)goto fail;
  g_signal_connect(t->script,"message",G_CALLBACK(on_message),t);
  frida_script_load_sync(t->script,NULL,&e);if(e)goto fail;
  gchar *config=g_strdup_printf("{\"type\":\"configure\",\"country\":\"%s\",\"iso3\":\"%s\",\"operator\":\"%s\",\"store\":%s}",t->country,t->iso3,t->operator,!strcmp(t->package,"com.sec.android.app.samsungapps")?"true":"false");
  frida_script_post(t->script,config,NULL);g_free(config);
  gint64 deadline=g_get_monotonic_time()+8*G_USEC_PER_SEC;
  while(!t->ready&&!stopping&&g_get_monotonic_time()<deadline){while(g_main_context_pending(NULL))g_main_context_iteration(NULL,FALSE);g_usleep(10000);}
  if(!t->ready)goto fail;
  t->attachments++;t->busy=FALSE;status();return;
fail:
  error_log(t->package,&e);detach(t);t->failed=TRUE;status();
}
static gboolean process_spawn(gpointer data) {
  FridaSpawn *spawn=data;guint pid=frida_spawn_get_pid(spawn);const char *id=frida_spawn_get_identifier(spawn);
  if(id&&!stopping)for(int i=0;i<MAX_TARGETS;i++)if(targets[i].package[0]&&!strcmp(id,targets[i].package)){attach_target(&targets[i],pid);break;}
  GError *e=NULL;frida_device_resume_sync(device,pid,NULL,&e);error_log("resume",&e);g_object_unref(spawn);return G_SOURCE_REMOVE;
}
static void on_spawn(FridaDevice *d,FridaSpawn *s,gpointer unused){g_idle_add(process_spawn,g_object_ref(s));}
static void stop_signal(int sig){stopping=1;}
static void resumed_done(GObject *source,GAsyncResult *result,gpointer unused) {
  GError *e=NULL;frida_device_resume_finish(device,result,&e);error_log("release",&e);pending_resumes--;finish_shutdown();
}
static void pending_done(GObject *source,GAsyncResult *result,gpointer unused) {
  GError *e=NULL;FridaSpawnList *list=frida_device_enumerate_pending_spawn_finish(device,result,&e);error_log("pending",&e);
  if(list){for(int i=0;i<frida_spawn_list_size(list);i++){
    FridaSpawn *s=frida_spawn_list_get(list,i);pending_resumes++;
    frida_device_resume(device,frida_spawn_get_pid(s),NULL,resumed_done,NULL);g_object_unref(s);
  }g_object_unref(list);}
  for(int i=0;i<MAX_TARGETS;i++)detach(&targets[i]);
  gate_closed=TRUE;finish_shutdown();
}
static void disabled_done(GObject *source,GAsyncResult *result,gpointer unused) {
  GError *e=NULL;frida_device_disable_spawn_gating_finish(device,result,&e);error_log("disable gating",&e);
  frida_device_enumerate_pending_spawn(device,NULL,pending_done,NULL);
}
static gboolean tick(gpointer unused){
  if(stopping){
    if(!shutdown_started){shutdown_started=TRUE;phase="stopping";status();frida_device_disable_spawn_gating(device,NULL,disabled_done,NULL);}
    return G_SOURCE_CONTINUE;
  }
  reload_config();return G_SOURCE_CONTINUE;
}
int main(int argc,char **argv) {
  if(geteuid()!=0)return 70;umask(0077);
  int lock=open(BASE "/controller.lock",O_CREAT|O_RDWR|O_NOFOLLOW,0600);
  if(lock<0||flock(lock,LOCK_EX|LOCK_NB))return 73;
  if(argc==2&&!strcmp(argv[1],"--daemon")){
    pid_t p=fork();if(p<0)return 74;if(p>0)return 0;setsid();
    int fd=open(BASE "/controller.log",O_CREAT|O_WRONLY|O_APPEND,0600),in=open("/dev/null",O_RDONLY);
    if(fd<0||in<0)return 74;dup2(in,0);dup2(fd,1);dup2(fd,2);close(in);if(fd>2)close(fd);
  }
  FILE *f=fopen("/proc/sys/kernel/random/boot_id","r");if(!f)return 74;fscanf(f,"%63s",boot);fclose(f);
  f=fopen(BASE "/controller.pid","w");if(!f)return 74;fprintf(f,"%d\n",getpid());fclose(f);
  signal(SIGTERM,stop_signal);signal(SIGINT,stop_signal);frida_init();
  pthread_t labeller;pthread_create(&labeller,NULL,label_agent,NULL);
  GError *e=NULL;FridaDeviceManager *manager=frida_device_manager_new();loop=g_main_loop_new(NULL,FALSE);status();
  if(!g_file_get_contents(BASE "/region.js",&region_source,NULL,&e))goto cleanup;
  reload_config();device=frida_device_manager_get_device_by_type_sync(manager,FRIDA_DEVICE_TYPE_LOCAL,0,NULL,&e);if(e)goto cleanup;
  g_signal_connect(device,"spawn-added",G_CALLBACK(on_spawn),NULL);
  frida_device_enable_spawn_gating_sync(device,NULL,&e);if(e)goto cleanup;
  gating=TRUE;phase="listening";status();g_timeout_add(200,tick,NULL);g_main_loop_run(loop);
cleanup:
  error_log("controller",&e);stopping=1;
  /* Shutdown callbacks have released spawn gating and all app sessions. */
  phase="stopped";status();pthread_join(labeller,NULL);
  close(lock);_exit(gating?0:1);
}
