package com.dawacare.stocktaking

import android.app.*
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

data class Drug(var name:String,var group:String,var system:Int,var actual:Int,var price:Double){
    val diff:Int get()=system-actual
    val missingValue:Double get()=if(diff>0) diff*price else 0.0
}
data class StockSession(var date:String,var drugs:MutableList<Drug>)

class MainActivity:Activity(){
    private val sessions=mutableListOf<StockSession>()
    private var current=0
    private lateinit var list:LinearLayout
    private lateinit var total:TextView
    private lateinit var search:EditText
    private val prefs by lazy{getSharedPreferences("dawacare_stock",Context.MODE_PRIVATE)}
    private val now get()=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date())

    override fun onCreate(b:Bundle?){super.onCreate(b);loadData();if(sessions.isEmpty())sessions.add(StockSession(now,mutableListOf()));showHome()}

    private fun tv(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setPadding(16,10,16,10)}
    private fun money(x:Double)="TSh ${String.format(Locale.US,"%,.0f",x)}"

    private fun showHome(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,16)}
        val title=tv("DawaCare Stock Taking",26f);title.setTextColor(Color.rgb(22,131,75));root.addView(title)
        root.addView(tv("Stock Taking: ${sessions[current].date}",18f))
        total=tv(summary(),18f);root.addView(total)

        search=EditText(this).apply{hint="🔍 Tafuta dawa kwa jina au kundi";setSingleLine(true)}
        root.addView(search)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
            override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){refresh(s?.toString()?:"")}
            override fun afterTextChanged(e:android.text.Editable?){}
        })

        val buttons=LinearLayout(this)
        buttons.addView(Button(this).apply{text="＋ Dawa";setOnClickListener{showDrugForm(-1)}})
        buttons.addView(Button(this).apply{text="＋ Stock Mpya";setOnClickListener{newSession()}})
        root.addView(buttons)
        val more=LinearLayout(this)
        more.addView(Button(this).apply{text="📋 Historia";setOnClickListener{showHistory()}})
        more.addView(Button(this).apply{text="📊 Ripoti";setOnClickListener{showReport()}})
        root.addView(more)

        val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);refresh()
    }

    private fun summary():String{
        val ds=sessions[current].drugs
        val missing=ds.sumOf{it.diff.coerceAtLeast(0)}
        val value=ds.sumOf{it.missingValue}
        return "Dawa: ${ds.size}   |   Zilizokosekana: $missing\nThamani iliyokosekana: ${money(value)}"
    }

    private fun refresh(q:String=""){
        total.text=summary();list.removeAllViews()
        val query=q.trim().lowercase()
        sessions[current].drugs.filter{query.isEmpty()||it.name.lowercase().contains(query)||it.group.lowercase().contains(query)}
            .forEachIndexed{_,d->
                val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)}
                box.addView(tv(d.name,19f));box.addView(tv("Kundi: ${d.group}"))
                box.addView(tv("Mfumo: ${d.system} | Halisi: ${d.actual} | Tofauti: ${d.diff.coerceAtLeast(0)}"))
                box.addView(tv("Bei: ${money(d.price)} | Thamani iliyokosekana: ${money(d.missingValue)}"))
                val actions=LinearLayout(this)
                actions.addView(Button(this).apply{text="Hariri";setOnClickListener{showDrugForm(sessions[current].drugs.indexOf(d))}})
                actions.addView(Button(this).apply{text="Futa";setOnClickListener{confirmDelete(sessions[current].drugs.indexOf(d))}})
                box.addView(actions);list.addView(box)
                list.addView(View(this).apply{setBackgroundColor(Color.LTGRAY)},LinearLayout.LayoutParams(-1,2))
            }
    }

    private fun showReport(){
        val ds=sessions[current].drugs
        val missing=ds.sumOf{it.diff.coerceAtLeast(0)}
        val value=ds.sumOf{it.missingValue}
        val shortage=ds.filter{it.diff>0}
        val lines=StringBuilder()
        lines.append("RIPOTI YA STOCK TAKING\n")
        lines.append("Tarehe: ${sessions[current].date}\n\n")
        lines.append("Jumla ya dawa: ${ds.size}\n")
        lines.append("Jumla ya dawa zilizokosekana: $missing\n")
        lines.append("Thamani yote iliyokosekana: ${money(value)}\n\n")
        lines.append("DAWA ZILIZOKOSEKANA:\n")
        if(shortage.isEmpty()) lines.append("Hakuna dawa iliyokosekana.")
        else shortage.forEachIndexed{i,d->lines.append("${i+1}. ${d.name} — ${d.diff} × ${money(d.price)} = ${money(d.missingValue)}\n")}
        AlertDialog.Builder(this).setTitle("Ripoti").setMessage(lines.toString()).setPositiveButton("Sawa",null).show()
    }

    private fun newSession(){sessions.add(StockSession(now,mutableListOf()));current=sessions.lastIndex;saveData();showHome()}

    private fun showHistory(){
        val names=sessions.mapIndexed{i,s->"${i+1}. ${s.date} — Dawa ${s.drugs.size} — ${money(s.drugs.sumOf{it.missingValue})}"}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Historia ya Stock Taking").setItems(names){_,w->current=w;showHome()}
            .setNegativeButton("Funga",null).show()
    }

    private fun showDrugForm(index:Int){
        val d=if(index>=0)sessions[current].drugs[index] else null
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        fun field(h:String,v:String="",num:Boolean=false)=EditText(this).apply{hint=h;setText(v);if(num)inputType=2}
        val name=field("Jina la dawa",d?.name?:"");form.addView(name)
        val group=field("Kundi la dawa",d?.group?:"");form.addView(group)
        val system=field("Idadi kwenye mfumo",d?.system?.toString()?:"",true);form.addView(system)
        val actual=field("Idadi halisi",d?.actual?.toString()?:"",true);form.addView(actual)
        val price=field("Bei ya kuuzia (TSh)",d?.price?.toString()?:"",true);form.addView(price)
        AlertDialog.Builder(this).setTitle(if(index>=0)"Hariri dawa" else "Ongeza dawa").setView(form)
            .setPositiveButton("Hifadhi"){_,_->
                val item=Drug(name.text.toString().trim(),group.text.toString().trim(),system.text.toString().toIntOrNull()?:0,actual.text.toString().toIntOrNull()?:0,price.text.toString().toDoubleOrNull()?:0.0)
                if(index>=0)sessions[current].drugs[index]=item else sessions[current].drugs.add(item)
                saveData();refresh(search.text.toString())
            }.setNegativeButton("Ghairi",null).show()
    }

    private fun confirmDelete(index:Int){
        AlertDialog.Builder(this).setTitle("Futa dawa?").setMessage("Uhakika unataka kufuta ${sessions[current].drugs[index].name}?")
            .setNegativeButton("Ghairi",null).setPositiveButton("Futa"){_,_->sessions[current].drugs.removeAt(index);saveData();refresh(search.text.toString())}.show()
    }

    private fun saveData(){
        val all=JSONArray()
        sessions.forEach{s->val o=JSONObject();o.put("date",s.date);val a=JSONArray()
            s.drugs.forEach{d->a.put(JSONObject().apply{put("name",d.name);put("group",d.group);put("system",d.system);put("actual",d.actual);put("price",d.price)})}
            o.put("drugs",a);all.put(o)}
        prefs.edit().putString("sessions",all.toString()).apply()
    }

    private fun loadData(){
        sessions.clear();val raw=prefs.getString("sessions",null)?:return
        try{val all=JSONArray(raw);for(i in 0 until all.length()){val o=all.getJSONObject(i);val ds=mutableListOf<Drug>();val a=o.getJSONArray("drugs")
            for(j in 0 until a.length()){val d=a.getJSONObject(j);ds.add(Drug(d.optString("name"),d.optString("group"),d.optInt("system"),d.optInt("actual"),d.optDouble("price")))}
            sessions.add(StockSession(o.optString("date"),ds))}}catch(_:Exception){}
    }
}
