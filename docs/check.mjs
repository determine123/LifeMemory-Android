import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
for (const [file, language, switchTo] of [['index.html','en','zh.html'],['zh.html','zh-CN','index.html']]) {
const html=readFileSync(new URL('./'+file,import.meta.url),'utf8');
assert.ok(html.includes(`lang="${language}"`));
assert.ok(html.includes(`href="${switchTo}"`));
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
assert.equal(ids.length,new Set(ids).size,'Duplicate HTML IDs');
for(const m of html.matchAll(/href="#([^"]+)"/g))assert.ok(ids.includes(m[1]),`Missing anchor ${m[1]}`);
const panels=Object.fromEntries(['capture','process','review'].map(id=>[id,{hidden:id!=='capture'}]));
const buttons=Object.keys(panels).map(id=>({dataset:{panel:id},listeners:{},setAttribute(k,v){this[k]=v},addEventListener(k,v){this.listeners[k]=v},focus(){this.focused=true}}));
const doc={querySelectorAll(){return buttons},getElementById(id){return panels[id]}};
new Function('document',html.match(/<script>([\s\S]*?)<\/script>/)[1])(doc);
buttons[1].listeners.click();
assert.equal(panels.process.hidden,false);assert.equal(panels.capture.hidden,true);
buttons[1].listeners.keydown({key:'ArrowRight',preventDefault(){}});
assert.equal(panels.review.hidden,false);assert.equal(buttons[2].focused,true);
console.log(`PASS ${file}: language, switch link, anchors, unique IDs, tabs and keyboard navigation`);
}
