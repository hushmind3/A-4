package com.dominion.mobile.data

import com.dominion.mobile.data.model.CharacterShape
import kotlin.math.*

/** Native vertex/normal data, triangles in model space. No downloaded model required. */
object AvatarMesh {
    private data class Point(val x: Float, val y: Float, val z: Float, val nx: Float, val ny: Float, val nz: Float)
    fun build(shape: CharacterShape): FloatArray {
        val lat = if (shape == CharacterShape.GEM) 8 else 24
        val lon = if (shape == CharacterShape.GEM) 8 else 40
        fun point(a: Int, b: Int): Point {
            val u = b.toDouble() / lon * 2 * PI
            val v = a.toDouble() / lat * if (shape == CharacterShape.DONUT) 2 * PI else PI
            if (shape == CharacterShape.DONUT) {
                val radius = 0.68 + 0.30 * cos(v)
                return Point((radius*cos(u)).toFloat(), (0.30*sin(v)).toFloat(), (radius*sin(u)).toFloat(),
                    (cos(v)*cos(u)).toFloat(), sin(v).toFloat(), (cos(v)*sin(u)).toFloat())
            }
            val x = sin(v)*cos(u)
            val y = cos(v)
            val z = sin(v)*sin(u)
            val radius = when (shape) {
                CharacterShape.STAR -> 0.79 + 0.20*cos(5*u)*sin(v).pow(2)
                CharacterShape.CLOUD -> 0.85 + 0.13*cos(4*u)*sin(v).pow(2) + 0.06*cos(3*v)
                CharacterShape.BLOB -> 0.87 + 0.07*sin(3*u)*sin(v).pow(2)
                else -> 0.89
            }
            val yScale = when (shape) {
                CharacterShape.BALLOON -> 1.15
                CharacterShape.CLOUD -> 0.75
                else -> 1.0
            }
            return Point((x*radius).toFloat(), (y*radius*yScale).toFloat(), (z*radius).toFloat(),
                x.toFloat(), (y/yScale).toFloat(), z.toFloat())
        }
        val out = ArrayList<Float>(lat*lon*36)
        fun triangle(p: Point,q: Point,r: Point) {
            var nx = (q.y-p.y)*(r.z-p.z)-(q.z-p.z)*(r.y-p.y)
            var ny = (q.z-p.z)*(r.x-p.x)-(q.x-p.x)*(r.z-p.z)
            var nz = (q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
            val length = sqrt(nx*nx+ny*ny+nz*nz)
            if (length < 0.000001f) return
            nx /= length; ny /= length; nz /= length
            if (nx*(p.nx+q.nx+r.nx)+ny*(p.ny+q.ny+r.ny)+nz*(p.nz+q.nz+r.nz) < 0f) {
                nx = -nx; ny = -ny; nz = -nz
            }
            for (vertex in listOf(p,q,r)) {
                out.add(vertex.x); out.add(vertex.y); out.add(vertex.z)
                if (shape == CharacterShape.GEM) {
                    out.add(nx); out.add(ny); out.add(nz)
                } else {
                    out.add(vertex.nx); out.add(vertex.ny); out.add(vertex.nz)
                }
            }
        }
        for (i in 0 until lat) for (j in 0 until lon) {
            val p=point(i,j); val q=point(i+1,j); val r=point(i+1,j+1); val t=point(i,j+1)
            triangle(p,q,r); triangle(p,r,t)
        }
        return out.toFloatArray()
    }
}
