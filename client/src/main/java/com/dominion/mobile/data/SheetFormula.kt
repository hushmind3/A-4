package com.dominion.mobile.data

import java.math.BigDecimal
import java.math.MathContext

/** Conservative Excel subset. Unknown functions, external references, shared/array formulas,
 * text coercions and cycles do not produce a fabricated numeric result. */
internal class SheetFormula(private val cells:Map<String,Cell>) {
    data class Cell(val number:BigDecimal?=null,val formula:String?=null,val unsupported:Boolean=false)
    private val cache=mutableMapOf<String,BigDecimal?>()
    private val stack=mutableSetOf<String>()
    private val math=MathContext.DECIMAL64
    private var budget=100_000
    fun evaluate(address:String):BigDecimal? {
        val id=address.replace("$","").uppercase(java.util.Locale.ROOT)
        if(cache.containsKey(id))return cache[id]
        if(stack.size>=64 || !stack.add(id) || --budget<0)return null
        val answer=try {
            val cell=cells[id]
            when {
                cell==null -> BigDecimal.ZERO
                cell.unsupported -> null
                cell.formula==null -> cell.number
                else -> Parser(cell.formula).parse()
            }
        }catch(_:Exception){null}finally{stack.remove(id)}
        cache[id]=answer
        return answer
    }
    private fun bounded(n:BigDecimal):BigDecimal {
        require(n.precision()<=32 && n.scale() in -100..100 && n.abs()<=BigDecimal("1E100"))
        return n
    }
    private inner class Parser(private val source:String) {
        private var at=0
        private var depth=0
        private fun space(){while(at<source.length && source[at].isWhitespace())at++}
        private fun eat(c:Char):Boolean {space();return if(at<source.length && source[at]==c){at++;true}else false}
        fun parse():BigDecimal {
            require(source.length in 1..1024)
            eat('=')
            val result=expr();space();require(at==source.length)
            return bounded(result)
        }
        private fun expr():BigDecimal {
            var x=term()
            while(true)x=when{eat('+')->bounded(x.add(term(),math));eat('-')->bounded(x.subtract(term(),math));else->return x}
        }
        private fun term():BigDecimal {
            var x=unary()
            while(true)x=when{eat('*')->bounded(x.multiply(unary(),math));eat('/')->bounded(x.divide(unary(),math));else->return x}
        }
        private fun unary():BigDecimal {
            require(++depth<=64 && --budget>=0)
            try {
                if(eat('+'))return unary()
                if(eat('-'))return unary().negate(math)
                var x=atom()
                while(eat('%'))x=x.movePointLeft(2)
                if(eat('^')){
                    val exponent=unary().intValueExact();require(exponent in -20..20)
                    x=bounded(x.pow(exponent,math))
                }
                return x
            }finally{depth--}
        }
        private fun atom():BigDecimal {
            if(eat('(')){val x=expr();require(eat(')'));return x}
            space();val start=at
            while(at<source.length && (source[at].isLetterOrDigit()||source[at] in "$._"))at++
            require(at>start)
            val token=source.substring(start,at).uppercase(java.util.Locale.ROOT)
            if(eat('(')){
                require(token in setOf("SUM","AVERAGE","MIN","MAX","COUNT"))
                val values=mutableListOf<BigDecimal>()
                if(!eat(')')){
                    do {
                        space();val old=at
                        val first=reference()
                        if(first!=null && eat(':')){
                            val last=requireNotNull(reference())
                            for(id in range(first,last)){
                                if(id in cells)values.add(requireNotNull(evaluate(id)))
                            }
                        }else{
                            at=old;values.add(expr())
                        }
                        require(values.size<=4096)
                    }while(eat(','))
                    require(eat(')'))
                }
                return when(token){
                    "SUM"->values.fold(BigDecimal.ZERO){a,b->bounded(a.add(b,math))}
                    "AVERAGE"->values.fold(BigDecimal.ZERO){a,b->bounded(a.add(b,math))}.divide(BigDecimal(values.size),math)
                    "MIN"->values.minOrNull()?:BigDecimal.ZERO
                    "MAX"->values.maxOrNull()?:BigDecimal.ZERO
                    else->BigDecimal(values.size)
                }
            }
            return token.toBigDecimalOrNull()?.let(::bounded) ?: run {
                require(ADDRESS.matches(token))
                requireNotNull(evaluate(token))
            }
        }
        private fun reference():String? {
            space();val m=ADDRESS.find(source,at) ?:return null
            if(m.range.first!=at)return null
            at=m.range.last+1
            return m.value.replace("$","").uppercase(java.util.Locale.ROOT)
        }
    }
    companion object {
        private val ADDRESS=Regex("\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]{0,6}")
        private fun range(a:String,b:String):List<String>{
            fun split(s:String):Pair<Int,Int>{
                val col=s.takeWhile(Char::isLetter).fold(0){n,c->n*26+(c.uppercaseChar()-'A')+1}
                val row=s.dropWhile(Char::isLetter).toInt()
                require(col in 1..16384 && row in 1..1048576)
                return col to row
            }
            fun col(n:Int):String{var v=n;var result="";while(v>0){v--;result=('A'+v%26)+result;v/=26};return result}
            val (x,y)=split(a);val (u,v)=split(b)
            require(x<=u && y<=v && (u-x+1L)*(v-y+1L)<=4096)
            return (y..v).flatMap{row->(x..u).map{c->col(c)+row}}
        }
    }
}
