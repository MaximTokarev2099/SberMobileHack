"use strict";
const $=id=>document.getElementById(id);
const layer=$("magicLayer"),orb=$("orb"),particles=$("particles"),answer=$("prediction"),ball=$("ballImage");
const names={summer:"magic_ball_summer.svg",autumn:"magic_ball_autumn.svg",winter:"magic_ball_winter.svg",spring:"magic_ball_spring.svg"};
const assets={summer:[],autumn:["autumn_maple.svg","autumn_oak.svg","autumn_birch.svg"],winter:["winter_snowflake_1.svg","winter_snowflake_2.svg","winter_snowflake_3.svg"],spring:["spring_daisy.svg","spring_petal.svg","spring_leaf.svg","spring_pink_flower.svg","spring_lilac_flower.svg"]};
let config=null,season="autumn",timers=[],busy=false,lastShake=0,motionPrev=0;
const later=(fn,ms)=>{const t=setTimeout(fn,ms);timers.push(t)};
function clearTimers(){timers.forEach(clearTimeout);timers=[]}
function particlesStart(){particles.replaceChildren();const list=assets[season];if(!list.length)return;const n=Math.min(35,Math.max(0,Number(config.particleCount)||20));for(let i=0;i<n;i++){const el=document.createElement("img");el.className="particle";el.src="./assets/particles/"+list[Math.floor(Math.random()*list.length)];el.alt="";el.style.left=Math.random()*100+"%";el.style.width=el.style.height=(14+Math.random()*23)+"px";el.style.setProperty("--dur",(3.2+Math.random()*4)+"s");el.style.setProperty("--delay",(-Math.random()*8)+"s");el.style.setProperty("--drift",(-80+Math.random()*160)+"px");el.style.setProperty("--spin",(Math.random()>.5?1:-1)*(170+Math.random()*540)+"deg");particles.append(el)}}
function setSeason(s){if(!names[s])return;season=s;$("season").value=s;ball.src="./assets/balls/"+names[s];orb.style.setProperty("--glow",config.glowColors[s]||"#9bdfff");if(busy)particlesStart()}
function hide(){clearTimers();busy=false;orb.className="";layer.classList.remove("visible");particles.replaceChildren();answer.textContent="";layer.setAttribute("aria-hidden","true")}
function show(){if(busy)return;busy=true;clearTimers();answer.textContent="";orb.className="";setSeason(season);particlesStart();layer.classList.add("visible");layer.setAttribute("aria-hidden","false");void orb.offsetWidth;orb.classList.add("arriving");later(()=>{orb.classList.add("settled");const text=config.answers[Math.floor(Math.random()*config.answers.length)];let i=0;const tick=()=>{if(!busy)return;answer.textContent=Array.from(text).slice(0,++i).join("");if(i<Array.from(text).length)later(tick,65)};tick()},1150);later(()=>{orb.classList.add("leaving");later(hide,470)},4900)}
function onMotion(e){const a=e.accelerationIncludingGravity||e.acceleration;if(!a)return;const mag=Math.hypot(a.x||0,a.y||0,a.z||0);const now=Date.now();if(Math.abs(mag-motionPrev)>13&&now-lastShake>1800){lastShake=now;show()}motionPrev=mag}
async function enableMotion(){if(!("DeviceMotionEvent" in window)){alert("Датчик движения недоступен. Используйте кнопку запуска.");return}try{if(typeof DeviceMotionEvent.requestPermission==="function"){const result=await DeviceMotionEvent.requestPermission();if(result!=="granted")throw Error("Нет разрешения")}window.addEventListener("devicemotion",onMotion);$("motion").textContent="Тряска включена"}catch(err){alert("Не удалось включить датчик: "+err.message)}}
async function init(){try{const response=await fetch("./config.json",{cache:"no-store"});if(!response.ok)throw Error("HTTP "+response.status);config=await response.json()}catch(e){config={season:"autumn",particleCount:20,glowColors:{},answers:["Всё получится!"]};console.warn("Конфигурация недоступна",e)}
const s=config.autoSeason?["winter","spring","summer","autumn"][Math.floor((new Date().getMonth()+1)%12/3)]:config.season;setSeason(s);
$("start").addEventListener("click",show);$("avatar").addEventListener("click",show);$("hide").addEventListener("click",hide);$("season").addEventListener("change",e=>setSeason(e.target.value));$("motion").addEventListener("click",enableMotion);
document.querySelectorAll(".tile").forEach(el=>el.addEventListener("click",()=>{$("testStatus").textContent="Нажатие прошло: "+el.textContent.trim()}));
}
window.MagicBall={show,hide,setSeason,enableMotion};
init();
