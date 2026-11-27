package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class VrmExpressionsTest {
 @Test fun usesExplicitBindingsAndOverride(){
  val json="""{"asset":{"version":"2.0"},"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"targets":[{},{}]}]}],"extensions":{"VRMC_vrm":{"expressions":{"preset":{"aa":{"morphTargetBinds":[{"node":0,"index":0,"weight":0.8}]},"happy":{"overrideMouth":"blend","morphTargetBinds":[{"node":0,"index":1,"weight":1.0}]}}}}}}"""
  val rig=VrmExpressions.parse(json.toByteArray(),false)!!
  val values=rig.weights("happy",0.5f,"aa",1f)
  assertEquals(0.4f,values.getValue(0 to 0),0.00001f)
  assertEquals(0.5f,values.getValue(0 to 1),0.00001f)
  assertTrue(rig.renderSource.decodeToString().contains(rig.nodeNames.getValue(0)))
  assertEquals(0f,rig.weights(null,0f,null,0f).getValue(0 to 0),0f)
 }
}
