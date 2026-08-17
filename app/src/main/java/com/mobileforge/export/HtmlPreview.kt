package com.mobileforge.export

import com.mobileforge.GameScene

/**
 * Standalone HTML snapshot of a scene. Not used as the app UI —
 * only an optional shareable preview file.
 */
object HtmlPreview {
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
