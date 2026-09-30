"""Build an offline catalog from the saved AOSP tables, preserving real PLMN entries."""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
table = (HERE / 'MccTable.java').read_text(encoding='utf-8')
carriers = (HERE / 'carrier_list.textpb').read_text(encoding='utf-8')
mccs = {mcc: (iso, int(length)) for mcc, iso, length in
        re.findall(r'new MccEntry\((\d{3}), "([a-z]{2})", ([23])\)', table)}
networks = sorted(set(re.findall(r'mccmnc_tuple: "(\d{5,6})"', carriers)))
options = {}
for network in networks:
    entry = mccs.get(network[:3])
    if entry and len(network) == 3 + entry[1]:
        options.setdefault(entry[0], []).append(network)
# Preserve the previously verified UK profile; select familiar listed networks for US/CA.
preferred = {'gb': '23415', 'us': '310260', 'ca': '302720'}
catalog = {iso: preferred.get(iso, values[0]) for iso, values in options.items()}
for iso, network in catalog.items():
    assert network in networks and mccs[network[:3]][0] == iso
target = HERE.parent / 'app/src/main/assets/countries.tsv'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(''.join(f'{iso}\t{network}\n' for iso, network in sorted(catalog.items())), encoding='utf-8')
(HERE / 'catalog-metadata.json').write_text(json.dumps({
    'count': len(catalog),
    'source_sha256': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                      for name in ['MccTable.java', 'carrier_list.textpb']},
    'representative_network_per_country': True,
}, indent=2) + '\n', encoding='utf-8')
print(f'{len(catalog)} country/territory profiles generated.')
