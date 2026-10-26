#!/usr/bin/env node
// Executes the actual embedded control script against fake official players.
// Protocol regressions only: no network, video decoding or real audio assertion.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/nabd/news/MediaEmbedPlayback.kt','utf8');
const template=source.split('<script>')[1].split('</script>')[0];
for(const provider of ['TikTok','YouTube','Vimeo']){
 const sent=[],calls=[],scripts=[],listeners={},frameListeners={};
 const origin=provider==='TikTok'?'https://www.tiktok.com':provider==='YouTube'?'https://www.youtube.com':'https://player.vimeo.com';
 const button={style:{display:'block'}};
 const frame={contentWindow:{postMessage:(data,recipient)=>sent.push({data,recipient})},addEventListener:(name,fn)=>frameListeners[name]=fn};
 const player={getPlayerState:()=>1,mute:()=>calls.push('mute'),unMute:()=>calls.push('unMute'),playVideo:()=>calls.push('play'),pauseVideo:()=>calls.push('pause')};
 const vimeo={setVolume:value=>{calls.push(['volume',value]);return Promise.resolve();},play:()=>{calls.push('play');return Promise.resolve();},pause:()=>{calls.push('pause');return Promise.resolve();},on:()=>{},ready:()=>Promise.resolve()};
 const context={document:{getElementById:id=>id==='player'?frame:button,createElement:()=>({}),head:{appendChild:s=>scripts.push(s)}},YT:{Player:function(){return player;}},Vimeo:{Player:function(){return vimeo;}}};
 context.window=context;context.addEventListener=(name,fn)=>listeners[name]=fn;
 const script=template.replace('${json.toJson(target.provider)}',JSON.stringify(provider)).replace('${json.toJson(origin)}',JSON.stringify(origin)).replace('$autoplay','true').replace('$muted','true');
 vm.runInNewContext(script,context);
 if(provider==='YouTube')context.onYouTubeIframeAPIReady();
 if(provider==='Vimeo')scripts[0].onload();
 context.nabdSetPlayback(true,false);
 if(provider==='TikTok'){
  assert(sent.some(x=>typeof x.data==='object'&&x.data.type==='unMute'&&x.data['x-tiktok-player']===true&&x.recipient===origin));
  const before=sent.length;
  listeners.message({origin:'https://evil.test',source:frame.contentWindow,data:{type:'onPlayerReady'}});
  assert.equal(sent.length,before);
  listeners.message({origin,source:frame.contentWindow,data:{type:'onPlayerReady','x-tiktok-player':true}});
  assert(sent.length>before);
 }else{
  assert(calls.some(x=>x==='unMute'||Array.isArray(x)&&x[0]==='volume'&&x[1]===1));
  assert(calls.includes('play'));assert.equal(scripts.length,1);
 }
 context.nabdSetPlayback(false,true);
 if(provider==='TikTok')assert(sent.some(x=>x.data.type==='mute')&&sent.some(x=>x.data.type==='pause'));
 else assert(calls.includes('pause'));
 assert.equal(frame.src,undefined); // Command application never reloads or seeks the iframe.
 console.log('PASS '+provider+' mute/unmute/play/pause protocol; no frame reload');
}
