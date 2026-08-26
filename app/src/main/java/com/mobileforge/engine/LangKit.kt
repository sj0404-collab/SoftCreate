package com.mobileforge.engine

object LangKit {
    val exts = listOf(
        "cs", "js", "ts", "tsx", "jsx", "kt", "java", "smali",
        "yml", "yaml", "xml", "json", "gradle", "kts",
        "py", "lua", "glsl", "hlsl", "cpp", "h", "c",
        "html", "css", "md", "txt", "obj", "gltf", "mat",
        "shader", "vb", "fs", "vs", "toml", "ini", "properties",
    )

    fun folderFor(ext: String): String = when (ext.lowercase()) {
        "cs", "js" -> "Scripts"
        "ts", "tsx", "jsx" -> "App"
        "kt", "java", "kts", "gradle" -> "Android"
        "smali" -> "Android/smali"
        "yml", "yaml", "toml" -> "Config"
        "py" -> "Blender"
        "lua" -> "Scripts"
        "glsl", "hlsl", "shader", "fs", "vs" -> "Assets/Shaders"
        "cpp", "h", "c" -> "Native"
        "html", "css" -> "Web"
        "obj", "gltf" -> "Assets/Meshes"
        "mat" -> "Assets/Materials"
        "xml" -> "Android/res"
        else -> "Docs"
    }

    fun seed(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        return when (ext) {
            "cs" -> "using MobileForge;\npublic class $name : ForgeBehaviour {\n    public float speed = 6f;\n    void Start() { }\n    void Update() { }\n}\n"
            "js" -> "function onUpdate(api, dt) {\n}\n"
            "ts" -> "export function main(): void {\n}\n"
            "tsx", "jsx" -> "export function $name() {\n  return <div className=\"panel\">$name</div>;\n}\n"
            "kt" -> "package app\n\nclass $name {\n}\n"
            "java" -> "package app;\n\npublic class $name {\n}\n"
            "smali" -> ".class public Lapp/$name;\n.super Ljava/lang/Object;\n.method public constructor <init>()V\n    .locals 0\n    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n    return-void\n.end method\n"
            "yml", "yaml" -> "name: $name\nversion: 1\n"
            "py" -> blenderPy(name, "cube", "#6ea8fe")
            "lua" -> "function on_update(dt)\nend\n"
            "json" -> "{\n  \n}\n"
            "xml" -> "<resources>\n</resources>\n"
            "html" -> "<!doctype html><meta charset=\"utf-8\"><title>$name</title>\n"
            "md" -> "# $name\n"
            else -> "// $path\n"
        }
    }

    fun blenderPy(name: String, shape: String, color: String): String = """
        # MobileForge → Blender 3.x/4.x
        # Откройте Blender: Scripting → Open → Run Script
        import bpy
        from mathutils import Vector

        def clear():
            bpy.ops.object.select_all(action='SELECT')
            bpy.ops.object.delete(use_global=False)

        clear()
        kind = "${shape.lowercase()}"
        if kind in ("uv", "sphere"):
            bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(0, 0, 1))
        elif kind in ("plane", "ground"):
            bpy.ops.mesh.primitive_plane_add(size=8, location=(0, 0, 0))
        elif kind in ("cylinder", "capsule"):
            bpy.ops.mesh.primitive_cylinder_add(radius=0.4, depth=2, location=(0, 0, 1))
        elif kind in ("cone", "pyramid"):
            bpy.ops.mesh.primitive_cone_add(radius1=1, depth=1.6, location=(0, 0, 0.8))
        elif kind in ("torus",):
            bpy.ops.mesh.primitive_torus_add(location=(0, 0, 0.5))
        else:
            bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0.5))
        obj = bpy.context.active_object
        obj.name = "$name"
        mat = bpy.data.materials.new(name="${name}_mat")
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        hexcol = "${color.lstrip('#')}"
        if len(hexcol) >= 6 and bsdf:
            r = int(hexcol[0:2], 16) / 255
            g = int(hexcol[2:4], 16) / 255
            b = int(hexcol[4:6], 16) / 255
            bsdf.inputs["Base Color"].default_value = (r, g, b, 1)
        obj.data.materials.append(mat)
        bpy.ops.export_scene.obj(filepath="//${name}.obj", use_selection=True)
        print("MobileForge blender: exported ${name}.obj")
    """.trimIndent() + "\n"
}
