package com.dominion.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit VRM morph bindings, not guessed mesh-name conventions. Only morph targets
 * are handled here; material color and texture-transform bindings are not supported. */
data class VrmMorph(val node:Int,val index:Int,val weight:Float)
data class VrmExpression(val name:String,val binary:Boolean,val overrideMouth:String,
    val binds:List<VrmMorph>)
class VrmExpressions private constructor(val expressions:Map<String,VrmExpression>,
    val nodeNames:Map<Int,String>,val renderSource:ByteArray) {
    fun weights(expression:String?,strength:Float,mouth:String?,level:Float):Map<Pair<Int,Int>,Float> {
        val values=linkedMapOf<Pair<Int,Int>,Float>()
        // Explicit zero on all managed targets also clears a previous expression.
        expressions.values.forEach{e->e.binds.forEach{values[it.node to it.index]=0f}}
        fun weight(e:VrmExpression,value:Float):Float =
            if(e.binary)if(value>0.5f)1f else 0f else value.coerceIn(0f,1f)
        fun add(e:VrmExpression?,value:Float){
            if(e==null)return
            val w=weight(e,value)
            e.binds.forEach { b ->
                val key=b.node to b.index
                values[key]=(values.getValue(key)+b.weight*w).coerceIn(0f,1f)
            }
        }
        val emotion=expressions[expression]
        val emotionWeight=emotion?.let{weight(it,strength)}?:0f
        val factor=when(emotion?.overrideMouth){
            "block"->if(emotionWeight>0f)0f else 1f
            "blend"->1f-emotionWeight
            else->1f
        }
        add(emotion,strength)
        if(mouth!=null)add(expressions[mouth]?:expressions["aa"],level*factor)
        return values
    }
    companion object {
        private fun obj(o:JsonObject?,key:String):JsonObject?=o?.get(key)?.takeIf{it.isJsonObject}?.asJsonObject
        private fun int(o:JsonObject,key:String):Int {
            val n=o.get(key)?.asBigDecimal?:error("Missing $key")
            return n.intValueExact().also{require(it>=0)}
        }
        fun parse(source:ByteArray,binary:Boolean):VrmExpressions? {
            val bytes=if(binary){
                val b=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
                b.position(12);val n=b.int;require(b.int==0x4e4f534a)
                ByteArray(n).also{b.get(it)}
            }else source
            val root=JsonParser.parseString(bytes.toString(Charsets.UTF_8).trimEnd(' ','\u0000','\n','\r','\t')).asJsonObject
            val ext=obj(root,"extensions")?:return null
            val nodes=root.getAsJsonArray("nodes")?:return null
            val meshes=root.getAsJsonArray("meshes")?:return null
            require(nodes.size()<=10000)
            val expressions=linkedMapOf<String,VrmExpression>()
            fun bind(node:Int,index:Int,w:Float):VrmMorph {
                require(node in 0 until nodes.size() && w.isFinite() && w in 0f..1f)
                val mesh=int(nodes[node].asJsonObject,"mesh")
                require(mesh in 0 until meshes.size())
                val primitives=meshes[mesh].asJsonObject.getAsJsonArray("primitives")
                require(primitives.size()>0 && primitives.any { p ->
                    index < (p.asJsonObject.getAsJsonArray("targets")?.size()?:0)
                })
                return VrmMorph(node,index,w)
            }
            val vrm1=obj(ext,"VRMC_vrm")
            if(vrm1!=null){
                val table=obj(vrm1,"expressions")
                for(group in listOf("preset","custom")) {
                    val values=obj(table,group)?:continue
                    for((name,element) in values.entrySet()){
                        require(name.length in 1..80 && name.none(Char::isISOControl))
                        val e=element.asJsonObject
                        val binds=e.getAsJsonArray("morphTargetBinds")?.map { item ->
                            val b=item.asJsonObject
                            bind(int(b,"node"),int(b,"index"),b.get("weight").asFloat)
                        }.orEmpty()
                        val override=e.get("overrideMouth")?.asString?:"none"
                        require(override in setOf("none","block","blend"))
                        if(binds.isNotEmpty())expressions[name]=VrmExpression(name,e.get("isBinary")?.asBoolean?:false,override,binds)
                    }
                }
            }else{
                val vrm0=obj(ext,"VRM")?:return null
                val groups=obj(vrm0,"blendShapeMaster")?.getAsJsonArray("blendShapeGroups")
                val aliases=mapOf("a" to "aa","i" to "ih","u" to "ou","e" to "ee","o" to "oh",
                    "joy" to "happy","angry" to "angry","sorrow" to "sad","fun" to "relaxed","blink" to "blink")
                groups?.forEach { item ->
                    val e=item.asJsonObject
                    val preset=e.get("presetName")?.asString.orEmpty().lowercase()
                    val name=aliases[preset]?:e.get("name")?.asString.orEmpty().take(80)
                    val binds=e.getAsJsonArray("binds")?.flatMap { b0 ->
                        val b=b0.asJsonObject
                        val mesh=int(b,"mesh");val index=int(b,"index");val weight=b.get("weight").asFloat/100f
                        nodes.mapIndexedNotNull { i,n ->
                            if(n.asJsonObject.get("mesh")?.asInt==mesh)bind(i,index,weight) else null
                        }
                    }.orEmpty()
                    if(name.isNotBlank() && binds.isNotEmpty())expressions[name]=VrmExpression(name,e.get("isBinary")?.asBoolean?:false,"none",binds)
                }
            }
            require(expressions.size<=128 && expressions.values.sumOf{it.binds.size}<=4096)
            if(expressions.isEmpty())return null
            val names=expressions.values.flatMap{it.binds}.map{it.node}.distinct().associateWith { node ->
                // Filament exposes node names rather than source glTF node indices. Give
                // each bound node an unambiguous internal name; retain original file bytes.
                "__dominion_vrm_node_"+node
            }
            val occupied=nodes.mapNotNull{it.asJsonObject.get("name")?.asString}.toSet()
            require(names.values.none{it in occupied})
            names.forEach{(node,name)->nodes[node].asJsonObject.addProperty("name",name)}
            val rewritten=root.toString().toByteArray(Charsets.UTF_8)
            val render=if(!binary)rewritten else {
                val old=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
                old.position(12);val jsonLength=old.int;old.int;old.position(20+jsonLength)
                val padded=(rewritten.size+3) and -4
                val output=ByteBuffer.allocate(20+padded+old.remaining()).order(ByteOrder.LITTLE_ENDIAN)
                output.putInt(0x46546c67).putInt(2).putInt(output.capacity()).putInt(padded).putInt(0x4e4f534a)
                output.put(rewritten);repeat(padded-rewritten.size){output.put(32.toByte())};output.put(old);output.array()
            }
            return VrmExpressions(expressions,names,render)
        }
    }
}
