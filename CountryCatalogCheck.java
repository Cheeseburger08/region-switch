import local.hessam.ukhooks.CountryProfile;
import java.nio.file.*;
import java.util.*;
public class CountryCatalogCheck {
    public static void main(String[] args)throws Exception{
        Set<String> seen=new HashSet<>();int count=0;
        for(String line:Files.readAllLines(Path.of(args[0]))){
            String[] f=line.split("\t");CountryProfile c=new CountryProfile(f[0],f[1]);
            if(!seen.add(c.iso)||c.name.isEmpty())throw new AssertionError("Invalid country");
            if(c.iso.equals("gb")&&!c.configLine("com.test.app").equals("com.test.app\tgb\tGBR\t23415"))throw new AssertionError("UK migration changed");
            count++;
        }
        if(count<200||!seen.containsAll(Arrays.asList("gb","us","ca","de","fr","ir","jp","kr","ae")))throw new AssertionError("Missing profiles");
        System.out.println(count+" country profiles validated, including UK backward compatibility.");
    }
}
