import Java from 'frida-java-bridge';

// Per-process overrides only. Configuration arrives before the app is resumed.
recv('configure', ({country, iso3, operator, store}) => {
  if (!/^[a-z]{2}$/.test(country) || !/^[A-Z]{3}$/.test(iso3) || !/^\d{5,6}$/.test(operator))
    throw new Error('Invalid region profile');
  Java.performNow(() => {
    const T = Java.use('android.telephony.TelephonyManager');
    for (const [name, value] of Object.entries({
      getSimCountryIso: country, getNetworkCountryIso: country,
      getSimOperator: operator, getNetworkOperator: operator,
    })) {
      if (!T[name]) continue;
      for (const overload of T[name].overloads) {
        if (overload.returnType.className !== 'java.lang.String') continue;
        overload.implementation = function () {
          send({event: 'country_override', method: name, value});
          return value;
        };
      }
    }
    if (store) {
      const prefs = Java.use('android.app.SharedPreferencesImpl');
      const original = prefs.getString.overload('java.lang.String', 'java.lang.String');
      const overrides = {
        real_country_code: iso3,
        SelectedMcc: operator.slice(0, 3),
        mcc_for_xml_cache_init: operator.slice(0, 3),
        mnc_for_xml_cache_init: operator.slice(3),
      };
      original.implementation = function (key, fallback) {
        const name = String(key);
        if (Object.prototype.hasOwnProperty.call(overrides, name)) {
          send({event: 'cached_region_override', key: name, value: overrides[name]});
          return overrides[name];
        }
        return original.call(this, key, fallback);
      };
    }
    send({event: 'hooks_ready', country, iso3, operator});
  });
});
