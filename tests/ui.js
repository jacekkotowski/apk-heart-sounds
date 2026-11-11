// Loads the screen in a headless browser with a stand-in for the Android side. Saves ui_*.png and exits with 1 on failure.
// Run: node tests/ui.js [output folder]. Needs Playwright for Node.
const { chromium } = require('playwright'); const path = require('path');
const STUB = `window.__c=[];window.__t=0;window.Android=new Proxy({},{get:(t,k)=>(...a)=>{__c.push(k+':'+a.join(','));
 if(k=='microphones')return JSON.stringify([{id:7,name:'Phone microphone (bottom) #7'},{id:9,name:'Phone microphone (back) #9'}]);
 if(k=='unprocessed')return true;
 if(k=='list')return JSON.stringify([{name:'2026-11-11_16-10-00.wav',rate:48000,secs:30,bytes:2880044}]);
 if(k=='rename')return true;
 if(k=='wave'){const d=[];const from=Math.max(a[0],__t-1200);for(let i=from;i<__t;i++){const ph=(i%83)/100,e=Math.exp(-((ph-.1)**2)/.0008)+.7*Math.exp(-((ph-.4)**2)/.0004),n=Math.abs(Math.sin(i*12.9898)*43758.5453%1)*.6;d.push((-(e*30+n)).toFixed(2),(e*28+n).toFixed(2),(e*20+n).toFixed(2));}return '{"n":'+__t+',"d":['+d.join(',')+']}';}
 if(k=='status'){if(window.__rec)__t+=10;return JSON.stringify({rec:window.__rec||false,count:window.__count||0,secs:12.4,limit:30,level:.35,clip:false,quality:window.__rec?'good':'',info:'48.0 kHz · unprocessed · mic bottom',busy:false,play:window.__play||null,pos:3,dur:30,msg:null});}
}})`;
(async () => {
  const out = process.argv[2] || __dirname, shot = n => path.join(out, n);
  const b = await chromium.launch(), pg = await b.newPage({ viewport: { width: 393, height: 830 }, deviceScaleFactor: 2 });
  const errs = []; pg.on('pageerror', e => errs.push(String(e)));
  await pg.addInitScript(STUB); await pg.goto('file://' + path.join(__dirname, '..', 'app', 'assets', 'index.html'));
  await pg.waitForTimeout(300); await pg.screenshot({ path: shot('ui_idle.png') });
  await pg.click('#seg button[data-f=wide]'); await pg.click('#adv'); await pg.selectOption('#hum', '50'); await pg.selectOption('#nr', '0.1');
  await pg.screenshot({ path: shot('ui_audio.png') }); await pg.click('#adv');
  await pg.click('#recb'); await pg.evaluate('window.__rec=true;window.__count=2'); await pg.waitForTimeout(300);
  const count = await pg.textContent('#clock');
  await pg.evaluate('window.__count=0'); await pg.waitForTimeout(7000);
  await pg.screenshot({ path: shot('ui_rec.png') });
  const view0 = await pg.getAttribute('#view button[data-v=smooth]', 'aria-pressed');
  await pg.click('#view button[data-v=wave]'); await pg.waitForTimeout(300); await pg.screenshot({ path: shot('ui_wave.png') });
  const clock = await pg.textContent('#clock'), q = await pg.textContent('#q'), dis = await pg.isDisabled('#dur');
  const ink = await pg.evaluate(`(()=>{const c=document.getElementById('scope'),d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let n=0;for(let i=0;i<d.length;i+=4)if(d[i+1]>150&&d[i]<160)n++;return n})()`);
  await pg.evaluate(`window.__rec=false;window.__play='2026-11-11_16-10-00.wav'`); await pg.waitForTimeout(400);
  for (const t of ['MORE', 'SAVE FILTERED WAV', 'SAVE GRAPH CSV', 'RENAME']) await pg.click(`button:text-is("${t}")`); await pg.waitForTimeout(200);
  await pg.screenshot({ path: shot('ui_play.png') });
  const seek = await pg.locator('#seek').count(), calls = await pg.evaluate('__c');
  const cfg = calls.filter(c => c.startsWith('config')).pop(), keep = calls.filter(c => c.startsWith('keepOn')).join(' ');
  const fits = await pg.evaluate('document.documentElement.scrollWidth<=window.innerWidth');
  const ok = !errs.length && cfg === 'config:9,0,20,600,50,0.1,30' && calls.includes('startRec:') && count === '-2' && clock === '00:12 / 00:30' && q.includes('good') && dis
    && ink > 2000 && view0 === 'true' && seek === 1 && keep === 'keepOn:true keepOn:false' && fits
    && calls.includes('saveFiltered:2026-11-11_16-10-00.wav') && calls.includes('saveCsv:2026-11-11_16-10-00.wav');
  console.log('config', cfg, '| countdown', count, '| clock', clock, '|', q, '| controls locked', dis, '| waveform pixels', ink, '| seek', seek, '|', keep, '| fits width', fits, '| errors', errs);
  console.log(ok ? 'UI PASS' : 'UI FAIL'); await b.close(); process.exit(ok ? 0 : 1);
})();
