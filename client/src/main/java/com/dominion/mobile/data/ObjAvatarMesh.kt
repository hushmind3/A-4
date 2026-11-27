package com.dominion.mobile.data

import kotlin.math.sqrt

/** Strict geometry-only OBJ subset. No MTL, textures, URLs or linked resources are loaded. */
object ObjAvatarMesh {
    const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_VERTICES = 100_000
    private const val MAX_TRIANGLES = 60_000
    private data class Vec(val x:Double,val y:Double,val z:Double) {
        operator fun minus(other:Vec)=Vec(x-other.x,y-other.y,z-other.z)
        fun unit():Vec {
            val l=sqrt(x*x+y*y+z*z)
            require(l.isFinite()&&l>1e-12)
            return Vec(x/l,y/l,z/l)
        }
        fun cross(other:Vec)=Vec(y*other.z-z*other.y,z*other.x-x*other.z,x*other.y-y*other.x)
    }
    private data class Ref(val vertex:Int,val normal:Int?)
    private data class Geometry(val vertices: List<Vec>, val normals: List<Vec>, val faces: List<List<Ref>>)
    private data class Normalization(val center: Vec, val scale: Double)
    data class Morph(val base: FloatArray, val open: FloatArray)

    fun parse(text: String): FloatArray {
        val geometry = geometry(text)
        return render(geometry, normalization(geometry), false)
    }

    /** Match vertex/face indices exactly and normalize both meshes with the base bounds. */
    fun morph(baseText: String, openText: String): Morph {
        val base = geometry(baseText)
        val open = geometry(openText)
        require(base.vertices.size == open.vertices.size && base.faces.size == open.faces.size)
        require(base.faces.indices.all { i ->
            base.faces[i].map { it.vertex } == open.faces[i].map { it.vertex }
        }) { "OBJ morph topology differs" }
        val transform = normalization(base)
        val a = render(base, transform, true)
        val b = render(open, transform, true)
        require(a.size == b.size && !a.contentEquals(b))
        // Keep model-scale errors or arbitrarily displaced geometry from becoming animations.
        require(a.indices.filter { it % 6 < 3 }.all { kotlin.math.abs(a[it] - b[it]) <= 2f })
        return Morph(a, b)
    }

    private fun geometry(text:String):Geometry {
        require(text.length in 1..MAX_BYTES)
        val vertices=ArrayList<Vec>()
        val normals=ArrayList<Vec>()
        val faces=ArrayList<List<Ref>>()
        fun index(value:String,count:Int):Int {
            val i=value.toInt()
            require(i!=0)
            val resolved=if(i>0)i-1 else count+i
            require(resolved in 0 until count)
            return resolved
        }
        fun vector(parts:List<String>):Vec {
            require(parts.size==4)
            val n=parts.drop(1).map { it.toDouble().also { v -> require(v.isFinite()&&kotlin.math.abs(v)<=1e12) } }
            return Vec(n[0],n[1],n[2])
        }
        for(raw in text.lineSequence()) {
            require(raw.length<=16384)
            val line=raw.substringBefore('#').trim()
            if(line.isEmpty())continue
            val parts=line.split(Regex("\\s+"))
            when(parts[0]){
                "v"->{require(vertices.size<MAX_VERTICES);vertices.add(vector(parts))}
                "vn"->{require(normals.size<MAX_VERTICES);normals.add(vector(parts).unit())}
                "f"->{
                    require(parts.size==4&&faces.size<MAX_TRIANGLES){"Triangulate the OBJ before import"}
                    faces.add(parts.drop(1).map { token ->
                        val p=token.split('/')
                        require(p.size in 1..3)
                        Ref(index(p[0],vertices.size),p.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { index(it,normals.size) })
                    })
                }
                "vt","o","g","s","usemtl","mtllib"->Unit // geometry only
                else->error("Unsupported OBJ record")
            }
        }
        require(vertices.isNotEmpty()&&faces.isNotEmpty())
        return Geometry(vertices, normals, faces)
    }

    private fun normalization(geometry: Geometry): Normalization {
        val (vertices, _, faces) = geometry
        val used=faces.flatMap { f->f.map { vertices[it.vertex] } }
        val lo=Vec(used.minOf { it.x },used.minOf { it.y },used.minOf { it.z })
        val hi=Vec(used.maxOf { it.x },used.maxOf { it.y },used.maxOf { it.z })
        val scale=maxOf(hi.x-lo.x,hi.y-lo.y,hi.z-lo.z)
        require(scale.isFinite()&&scale>1e-12)
        val center=Vec((hi.x+lo.x)/2,(hi.y+lo.y)/2,(hi.z+lo.z)/2)
        return Normalization(center, scale)
    }

    private fun render(geometry: Geometry, transform: Normalization, strict: Boolean): FloatArray {
        val (vertices, normals, faces) = geometry
        val (center, scale) = transform
        val result=FloatArray(faces.size*18)
        var at=0
        for(face in faces){
            val a=vertices[face[0].vertex];val b=vertices[face[1].vertex];val c=vertices[face[2].vertex]
            val n=(b-a).cross(c-a)
            val l=sqrt(n.x*n.x+n.y*n.y+n.z*n.z)
            if(!l.isFinite()||l<=1e-12) {
                require(!strict) { "Degenerate triangle in morph" }
                continue
            }
            val faceNormal=n.unit()
            for(ref in face){
                val p=vertices[ref.vertex]-center
                val normal=ref.normal?.let { normals[it] }?:faceNormal
                for(v in listOf(p.x*2/scale,p.y*2/scale,p.z*2/scale,normal.x,normal.y,normal.z))
                    result[at++]=v.toFloat().also { require(it.isFinite()) }
            }
        }
        require(at>=18)
        return result.copyOf(at)
    }
}
