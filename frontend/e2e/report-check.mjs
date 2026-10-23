import { chromium, expect } from '@playwright/test';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const out=resolve(repo,'test-results/report-evidence');
await mkdir(out,{recursive:true});
const browser=await chromium.launch({...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{})});
const evidence=[];
try {
 for(const number of [1,2,3]) {
  const filename=resolve(repo,`docs/reports/2026-10-23/flow-pr${number}-implementation-report-20261023.html`);
  const content=await readFile(filename,'utf8');
  const legacy=number===1;
  const data=JSON.parse(content.match(legacy?/<script id="source-data" type="application\/json">([\s\S]*?)<\/script>/:/<script id="report-data" type="application\/json">([\s\S]*?)<\/script>/)[1]);
  const sources=Object.fromEntries((legacy?data:data.items).map(x=>[x.id,x]));
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto(pathToFileURL(filename).href);
  const link=page.locator(legacy?'#source-detail a.source-link':'#source-link');
  let clicks=0,keys=0;
  const groups=legacy?[{id:null}]:data.graphs;
  for(const group of groups) {
   if(group.id)await page.locator('#tab-'+group.id).click();
   const nodes=page.locator(legacy?'svg g[data-id]':`#graph-${group.id} g[data-source]`);
   for(let i=0;i<await nodes.count();i++) {
    const node=nodes.nth(i);const id=await node.getAttribute(legacy?'data-id':'data-source');
    await node.click();clicks++;await expect(link).toHaveAttribute('href',sources[id].url);
    for(const key of ['Enter','Space']) {await node.focus();await page.keyboard.press(key);keys++;await expect(link).toHaveAttribute('href',sources[id].url);}
   }
  }
  if(number===3) {
   const left=await page.locator('.source-panel').boundingBox();const right=await page.locator('.diagram-panel').boundingBox();
   if(!(left.x+left.width<=right.x))throw new Error('Desktop sources must be left of diagrams');
   const overflow=await page.locator('g.node').evaluateAll(nodes=>nodes.flatMap(n=>[...n.querySelectorAll('text')].filter(t=>{const b=t.getBBox();return b.x<0||b.x+b.width>210;}).map(t=>t.textContent)));
   if(overflow.length)throw new Error('Source node labels overflow: '+overflow.join(', '));
  }
  if(!legacy)await page.locator('.explorer').screenshot({path:resolve(out,`pr${number}-desktop.png`)});
  await page.setViewportSize({width:390,height:844});
  const widths=await page.evaluate(()=>({page:document.documentElement.scrollWidth,viewport:innerWidth}));
  if(widths.page>widths.viewport)throw new Error(`PR${number} page overflows mobile viewport: ${JSON.stringify(widths)}`);
  await page.screenshot({path:resolve(out,`pr${number}-mobile.png`)});
  if(errors.length)throw new Error('Report JavaScript errors: '+errors.join('; '));
  evidence.push({report:number,clicks,keyboardSelections:keys,mobileWidths:widths,pageErrors:0});
  await page.close();
 }
 await writeFile(resolve(out,'report-evidence.json'),JSON.stringify({result:'PASS',reports:evidence},null,2));
 console.log('PASS report interaction and layout: '+JSON.stringify(evidence));
} finally {await browser.close();}
