from __future__ import annotations
import json,uuid,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
version=json.loads((ROOT/'config/hydra-version.json').read_text(encoding='utf-8'))['version']
components=[];seen=set()
def add(component):
    key=(component.get('purl',''),component.get('name',''),component.get('version',''))
    if key in seen:return
    seen.add(key);components.append(component)
lock=json.loads((ROOT/'frontend/package-lock.json').read_text(encoding='utf-8'))
for name,item in sorted(lock.get('packages',{}).items()):
    if not name.startswith('node_modules/'):continue
    pkg=name.removeprefix('node_modules/');ver=str(item.get('version',''))
    add({'type':'library','group':'npm','name':pkg,'version':ver,'purl':f"pkg:npm/{pkg}@{ver}"})
ns={'m':'http://maven.apache.org/POM/4.0.0'}
tree=ET.parse(ROOT/'pom.xml');project=tree.getroot()
properties_element=project.find('m:properties',ns)
props={child.tag.split('}',1)[-1]:(child.text or '').strip() for child in ([] if properties_element is None else list(properties_element))}
def resolve(value):
    text=(value or '').strip()
    if text.startswith('${') and text.endswith('}'):return props.get(text[2:-1],text)
    return text
for dep in project.findall('m:dependencies/m:dependency',ns):
    group=(dep.findtext('m:groupId',default='',namespaces=ns) or '').strip();name=(dep.findtext('m:artifactId',default='',namespaces=ns) or '').strip()
    ver=resolve(dep.findtext('m:version',default='',namespaces=ns)) or 'managed-by-parent'
    scope=(dep.findtext('m:scope',default='compile',namespaces=ns) or 'compile').strip()
    purl=f'pkg:maven/{group}/{name}' + (f'@{ver}' if ver!='managed-by-parent' else '')
    add({'type':'library','group':group,'name':name,'version':ver,'scope':scope,'purl':purl,'properties':[{'name':'hydra:source','value':'pom.xml'}]})
components.sort(key=lambda c:(c.get('group',''),c['name'],c.get('version','')))
serial='urn:uuid:'+str(uuid.uuid5(uuid.NAMESPACE_URL,f'hydra-release:{version}'))
sbom={'bomFormat':'CycloneDX','specVersion':'1.5','serialNumber':serial,'version':1,
      'metadata':{'component':{'type':'application','name':'HYDRA','version':version}},'components':components}
out=ROOT/'target/release'/f'HYDRA_V{version}_sbom.cdx.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(sbom,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(out, len(components))
