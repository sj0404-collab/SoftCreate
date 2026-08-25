package com.mobileforge.export

import com.mobileforge.GameScene

/**
 * Standalone HTML snapshot of a scene. Not used as the app UI —
 * only an optional shareable preview file.
 */
object HtmlPreview {
    fun player(scene: GameScene, controls: com.mobileforge.engine.ControlLayout): String {
        val objects = scene.objects.joinToString(",") { obj ->
            """{"name":${obj.name.quote()},"type":${obj.type.quote()},"x":${obj.x},"y":${obj.y},"z":${obj.z},"sx":${obj.sx},"sy":${obj.sy},"sz":${obj.sz},"color":${obj.color.quote()}}"""
        }
        val pad = if (controls.items.isEmpty()) "1" else "0"
        return """
            <!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${scene.name}</title>
            <body style="margin:0;background:#0b0d14;color:#eee;font-family:sans-serif;touch-action:none">
            <canvas id="c" style="width:100%;height:100vh;display:block"></canvas>
            <div id="hud" style="position:fixed;top:8px;left:10px;font:14px sans-serif">score 0</div>
            <script>
            const scene={dimension:${scene.dimension.quote()},objects:[$objects]};
            const needPad=$pad;
            const c=document.getElementById('c'),x=c.getContext('2d');
            let inp={x:0,y:0,jump:false},score=0;
            function fit(){c.width=innerWidth;c.height=innerHeight}fit();addEventListener('resize',fit);
            addEventListener('keydown',e=>{if(e.key==='a'||e.key==='ArrowLeft')inp.x=-1;if(e.key==='d'||e.key==='ArrowRight')inp.x=1;if(e.key==='w'||e.key==='ArrowUp')inp.y=1;if(e.key==='s'||e.key==='ArrowDown')inp.y=-1;if(e.key===' ')inp.jump=true;});
            addEventListener('keyup',e=>{if(e.key==='a'||e.key==='d'||e.key==='ArrowLeft'||e.key==='ArrowRight')inp.x=0;if(e.key==='w'||e.key==='s'||e.key==='ArrowUp'||e.key==='ArrowDown')inp.y=0;});
            let last=0;
            function loop(t){
              const dt=Math.min(0.05,(t-last)/1000||0.016);last=t;
              const p=scene.objects.find(o=>o.type==='Player');
              if(p){p.x+=inp.x*6*dt;p.z+=-inp.y*6*dt;if(inp.jump){p.y+=0.4;inp.jump=false;}if(p.y>1)p.y-=8*dt;if(p.y<1)p.y=1;}
              x.fillStyle='#0b0d14';x.fillRect(0,0,c.width,c.height);
              scene.objects.forEach(o=>{
                x.fillStyle=o.color||'#b69cff';
                const px=c.width/2+o.x*22, py=c.height/2-o.y*22+o.z*8;
                x.fillRect(px,py,Math.max(8,o.sx*16),Math.max(8,o.sy*16));
                x.fillStyle='#fff';x.font='12px sans-serif';x.fillText(o.name,px,py-4);
              });
              document.getElementById('hud').textContent='score '+score+' · WASD / стик';
              requestAnimationFrame(loop);
            }
            requestAnimationFrame(loop);
            if(needPad){
              const s=document.createElement('div');
              s.style.cssText='position:fixed;left:16px;bottom:16px;width:110px;height:110px;border-radius:55px;background:#b69cff33;border:1px solid #555';
              document.body.appendChild(s);
              s.addEventListener('touchmove',e=>{e.preventDefault();const r=s.getBoundingClientRect();const t=e.touches[0];
                inp.x=((t.clientX-r.left)/r.width*2-1);inp.y=-((t.clientY-r.top)/r.height*2-1);},{passive:false});
              s.addEventListener('touchend',()=>{inp.x=0;inp.y=0;});
              const b=document.createElement('div');
              b.textContent='прыжок';b.style.cssText='position:fixed;right:16px;bottom:24px;padding:14px 18px;background:#b69cffcc;border-radius:12px;color:#111';
              b.ontouchstart=()=>{inp.jump=true};
              document.body.appendChild(b);
            }
            </script></body>
        """.trimIndent()
    }

    fun render(scene: GameScene): String {
        val objects = scene.objects.joinToString(",") { obj ->
            """{"name":${obj.name.quote()},"type":${obj.type.quote()},"x":${obj.x},"y":${obj.y},"z":${obj.z},"sx":${obj.sx},"sy":${obj.sy},"sz":${obj.sz},"color":${obj.color.quote()}}"""
        }
        return """
            <!doctype html><meta charset="utf-8"><title>${scene.name} preview</title>
            <body style="margin:0;background:#0b0d14;color:#eee;font-family:sans-serif">
            <div style="padding:10px">MobileForge preview · ${scene.name} · ${scene.dimension}</div>
            <canvas id="c" style="width:100%;height:90vh;display:block"></canvas>
            <script>
            const scene={dimension:${scene.dimension.quote()},objects:[$objects]};
            const c=document.getElementById('c'),x=c.getContext('2d');
            function fit(){c.width=innerWidth;c.height=innerHeight-40}fit();addEventListener('resize',fit);
            function loop(){x.fillStyle='#0b0d14';x.fillRect(0,0,c.width,c.height);
              scene.objects.forEach((o,i)=>{x.fillStyle=o.color||'#b69cff';
                const px=c.width/2+o.x*18, py=c.height/2-o.y*18+o.z*6;
                x.fillRect(px,py,Math.max(8,o.sx*16),Math.max(8,o.sy*16));
                x.fillStyle='#fff';x.font='12px sans-serif';x.fillText(o.name,px,py-4);});
              requestAnimationFrame(loop)}loop();
            </script></body>
        """.trimIndent()
    }

    private fun String.quote(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
