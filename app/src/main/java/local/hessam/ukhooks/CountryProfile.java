package local.hessam.ukhooks;
import java.util.Locale;

public final class CountryProfile {
    public final String iso, iso3, operator, name;
    public CountryProfile(String iso,String operator){
        if(!iso.matches("[a-z]{2}")||!operator.matches("[0-9]{5,6}"))throw new IllegalArgumentException("Invalid country profile");
        Locale country=new Locale("",iso.toUpperCase(Locale.ROOT));
        this.iso=iso;this.iso3=country.getISO3Country();this.name=country.getDisplayCountry(Locale.ENGLISH);this.operator=operator;
        if(!iso3.matches("[A-Z]{3}"))throw new IllegalArgumentException("Country has no ISO3 code");
    }
    public String configLine(String packageName){return packageName+"\t"+iso+"\t"+iso3+"\t"+operator;}
}
