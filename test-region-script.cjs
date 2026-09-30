const fs=require('fs'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync(require('path').join(__dirname, 'hooks/region-switch.js'),'utf8').replace(/^import .*;\r?\n/,'');
function run(config){
  let callback,usedPrefs=false;const events=[];
  const T={};for(const key of ['getSimCountryIso','getNetworkCountryIso','getSimOperator','getNetworkOperator'])T[key]={overloads:[{returnType:{className:'java.lang.String'}}]};
  const original={call:(_self,key,fallback)=>'original:'+key+':'+fallback};
  vm.runInNewContext(code,{recv:(type,fn)=>{assert.equal(type,'configure');callback=fn},send:e=>events.push(e),Java:{performNow:fn=>fn(),use:name=>{
    if(name==='android.telephony.TelephonyManager')return T;
    assert.equal(name,'android.app.SharedPreferencesImpl');usedPrefs=true;return {getString:{overload:()=>original}};
  }}});
  callback(config);return {T,original,events,usedPrefs};
}
for(const c of [{country:'gb',iso3:'GBR',operator:'23415',store:true},{country:'us',iso3:'USA',operator:'310260',store:true},{country:'de',iso3:'DEU',operator:'26201',store:false}]){
  const r=run(c);assert.equal(r.T.getSimCountryIso.overloads[0].implementation(),c.country);assert.equal(r.T.getNetworkCountryIso.overloads[0].implementation(),c.country);
  assert.equal(r.T.getSimOperator.overloads[0].implementation(),c.operator);assert.equal(r.T.getNetworkOperator.overloads[0].implementation(),c.operator);
  assert.equal(r.usedPrefs,c.store);assert(r.events.some(e=>e.event==='hooks_ready'));
  if(c.store){for(const [key,want] of Object.entries({real_country_code:c.iso3,SelectedMcc:c.operator.slice(0,3),mcc_for_xml_cache_init:c.operator.slice(0,3),mnc_for_xml_cache_init:c.operator.slice(3)}))assert.equal(r.original.implementation(key,'fallback'),want);
    assert.equal(r.original.implementation('unrelated_setting','keep'),'original:unrelated_setting:keep');}
}
assert.throws(()=>run({country:'us;id',iso3:'USA',operator:'310260',store:true}),/Invalid region/);
console.log('Country overrides, Galaxy Store cached region, generic isolation, and input validation passed.');
