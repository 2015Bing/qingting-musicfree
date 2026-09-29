// One-time dependency: npm install --prefix .tools/icon-render @resvg/resvg-js
// Renders our source SVG; does not modify the AI concept raster.
const fs=require('node:fs');
const {Resvg}=require('../.tools/icon-render/node_modules/@resvg/resvg-js');
const dir='assets/brand';
const mark=fs.readFileSync(`${dir}/qingting-mark.svg`,'utf8').replace(/<svg[^>]*>|<\/svg>/g,'');
function svg(content){return `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72">${content}</svg>`;}
const icon=svg(`<rect x="18" y="18" width="72" height="72" rx="16" fill="#245E4F"/>${mark}`);
fs.writeFileSync(`${dir}/qingting-logo.svg`,icon);
fs.writeFileSync(`${dir}/qingting-logo.png`,new Resvg(icon).render().asPng());
fs.writeFileSync(`${dir}/qingting-circle.png`,new Resvg(svg(`<defs><clipPath id="mask"><circle cx="54" cy="54" r="36"/></clipPath></defs><g clip-path="url(#mask)"><rect x="18" y="18" width="72" height="72" fill="#245E4F"/>${mark}</g>`)).render().asPng());
fs.writeFileSync(`${dir}/qingting-themed.png`,new Resvg(svg(`<rect x="18" y="18" width="72" height="72" rx="16" fill="#DCE9D7"/>${mark.replaceAll('#F7F8F4','#245E4F')}`)).render().asPng());
fs.writeFileSync(`${dir}/qingting-small.png`,new Resvg(icon,{fitTo:{mode:'width',value:48}}).render().asPng());
