// Renders the report from the JSON that tests/run.sh writes (synthetic beats), at phone width and as an A4 PDF.
// Saves report_*.png and report_*.pdf, and exits with 1 on a script error, sideways overflow, a missing caveat, a disease name,
// or a PDF longer than 4 pages. Run: bash tests/run.sh && node tests/report.js [output folder] [extra report.json ...]. Needs Playwright for Node.
const { chromium } = require('playwright'); const path = require('path'), fs = require('fs');
const root = path.join(__dirname, '..'), url = 'file://' + path.join(root, 'app', 'assets', 'report.html');
const DISEASE = /stenosis|regurgitation|arrhythmia|fibrillation|infarct|heart failure|cardiomyopathy|tachycardia|bradycardia|prolapse|septal defect/i;
(async () => {
  const out = process.argv[2] || __dirname, extra = process.argv.slice(3), b = await chromium.launch(); let fails = 0;
  const check = (what, ok) => { console.log((ok ? 'PASS  ' : 'FAIL  ') + what); if (!ok) fails++; };
  const cases = ['plain', 'flag', 'none'].map(n => [n, path.join(root, 'out', 'test', 'report_' + n + '.json')]).concat(extra.map((f, i) => ['real' + (i + 1), f]));
  for (const [name, file] of cases) {
    if (!fs.existsSync(file)) { console.log('missing ' + file + ': run bash tests/run.sh first'); process.exit(1); }
    const data = fs.readFileSync(file, 'utf8'), errs = [];
    const pg = await b.newPage({ viewport: { width: 393, height: 830 }, deviceScaleFactor: 2 }); pg.on('pageerror', e => errs.push(String(e))); pg.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
    await pg.addInitScript('window.REPORT_DATA=' + data); const t0 = Date.now(); await pg.goto(url); await pg.waitForFunction('window.__ready'); const ms = Date.now() - t0;
    await pg.screenshot({ path: path.join(out, 'report_' + name + '_phone.png'), fullPage: true });
    const text = await pg.evaluate('document.getElementById("rep").innerText'), all = await pg.evaluate('document.getElementById("rep").textContent');
    const fits = await pg.evaluate('document.documentElement.scrollWidth<=window.innerWidth');
    const wideEl = await pg.evaluate('[...document.querySelectorAll("#rep *")].filter(e=>!e.closest(".scroll")&&!e.closest("svg")&&e.getBoundingClientRect().right>window.innerWidth+1).length');
    const d = JSON.parse(data), rel = d.a.reliable, ecg = (all.match(/ECG/g) || []).length, notEcg = (all.match(/not an ECG/g) || []).length;
    const charts = await pg.evaluate('[...document.querySelectorAll("#rep svg.ch")].length'), empty = await pg.evaluate('[...document.querySelectorAll("#rep [id^=c_]")].filter(e=>!e.firstChild).map(e=>e.id).join(",")');
    const bad = await pg.evaluate('document.getElementById("rep").innerHTML.replace(/href="data:[^"]*"/g,"").match(/NaN|undefined|Infinity/g)');
    check(name + ': no script errors ' + errs.join(' | '), !errs.length);
    check(name + ': fits the phone width, nothing sticks out sideways', fits && wideEl === 0);
    check(name + ': says it is not an ECG and not a diagnosis; every mention of ECG is a denial', all.includes('not an ECG and not a diagnosis') && ecg === notEcg);
    check(name + ': the flag result carries its caveat (experimental, children, not phones)', /experimental: it was tested on stethoscope recordings of children, not on phones or adults/.test(all));
    check(name + ': no disease is named', !DISEASE.test(all));
    check(name + ': no NaN or undefined on the page, no empty chart slot (' + empty + ')', !bad && !empty);
    if (rel) check(name + ': rate, typical beat and all charts are shown (' + charts + ' charts)', text.includes(Math.round(d.a.rate) + '\nbeats per minute') && charts >= 9 && all.includes('gap 1 · ' + d.beat.s1s2.toFixed(2) + ' s'));
    else check(name + ': no rate is shown, the reason is, and signal, spectrum and advice remain (' + charts + ' charts)', all.includes('not found reliably') && !/\d\s*beats per minute/.test(text) && text.includes('not measured') && charts >= 4 && all.includes('How to get a better recording') && all.includes('Average spectrum'));
    if (name === 'flag') check('flag: flagged sentence with the doctor caveat', all.includes('A murmur-like sound was picked up') && all.includes('Only a doctor listening with a stethoscope can tell'));
    if (name === 'plain') {
      check('plain: not-flagged sentence says it rules nothing out', all.includes('No murmur-like sound was picked up') && all.includes('does not rule out'));
      check('plain: unusual beat is listed with its reason', all.includes('knock or loud noise'));
      const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#x_csv')]); const csv = fs.readFileSync(await dl.path(), 'utf8').split('\n');
      check('plain: CSV export has a header and one row per beat (' + (csv.length - 2) + ')', csv[0].startsWith('beat,time_s,length_s,s1_s2_s') && csv.length - 2 === d.beats.t.length && dl.suggestedFilename() === 'synthetic-60s_beats.csv');
      const [dj] = await Promise.all([pg.waitForEvent('download'), pg.click('#x_json')]);
      check('plain: JSON export reopens as the same report', JSON.parse(fs.readFileSync(await dj.path(), 'utf8')).a.rate === d.a.rate);
      await pg.hover('#c_spec svg', { position: { x: 150, y: 60 } }); check('plain: hovering a chart shows a value', /Hz/.test(await pg.textContent('#tip')));
      await pg.click('.tile summary'); check('plain: tapping a number opens its explanation', await pg.isVisible('.tile details div'));
      check('plain: opens in under 3 s on this computer (' + ms + ' ms)', ms < 3000);
    }
    await pg.close();
    // A4: PDF and a picture of the print layout.
    const pr = await b.newPage({ viewport: { width: 794, height: 1123 } }); pr.on('pageerror', e => errs.push(String(e)));
    await pr.addInitScript('window.REPORT_DATA=' + data); await pr.goto(url); await pr.waitForFunction('window.__ready');
    const pdf = await pr.pdf({ path: path.join(out, 'report_' + name + '.pdf'), format: 'A4', preferCSSPageSize: true, printBackground: true });
    const pages = (pdf.toString('latin1').match(/\/Type\s*\/Page\b(?!s)/g) || []).length;
    await pr.emulateMedia({ media: 'print' }); await pr.evaluate('window.dispatchEvent(new Event("beforeprint"))'); await pr.screenshot({ path: path.join(out, 'report_' + name + '_a4.png'), fullPage: true });
    check(name + ': PDF has at most 4 pages (' + pages + ')', pages >= 1 && pages <= 4);
    await pr.close();
  }
  // Inside the app: a stand-in for the host that supplies the data and takes saves, print and the chest position.
  const pg = await b.newPage({ viewport: { width: 393, height: 830 } }); const errs = []; pg.on('pageerror', e => errs.push(String(e)));
  await pg.addInitScript('window.__c=[];window.__n=0;window.ReportHost={start:n=>__c.push("start:"+n),poll:()=>++__n<3?"null":JSON.stringify(' + fs.readFileSync(cases[0][1], 'utf8') + '),message:()=>null,save:(f,m,t)=>__c.push("save:"+f+":"+m+":"+t.length),print:j=>__c.push("print:"+j),sharePdf:j=>__c.push("share:"+j),setPosition:(n,p)=>__c.push("pos:"+n+":"+p)}');
  await pg.goto(url + '#' + encodeURIComponent('synthetic-60s.wav')); await pg.waitForFunction('window.__ready');
  await pg.click('#x_pdf'); await pg.click('#x_share'); await pg.click('#x_csv'); await pg.click('#x_json'); await pg.click('#c_chest .spot[data-pos=apex]');
  const calls = (await pg.evaluate('__c')).join(' '), posName = await pg.textContent('#posname');
  check('in the app: data comes from the host; PDF, share, CSV, JSON and chest position go to it', !errs.length && calls.startsWith('start:synthetic-60s.wav print:synthetic-60s_report share:synthetic-60s_report save:synthetic-60s_beats.csv:text/csv:') && calls.includes('save:synthetic-60s_report.json:application/json:') && calls.endsWith('pos:synthetic-60s.wav:apex') && posName.includes('apex'));
  console.log(fails ? fails + ' FAILED' : 'REPORT PASS'); await b.close(); process.exit(fails ? 1 : 0);
})();
