package com.n0n3m4.reducenative

/*
	The author of this code is n0n3m4, this code is public domain
 */

import android.os.Bundle
import android.os.Handler
import android.support.annotation.UiThread
import android.support.annotation.WorkerThread
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.ListPopupWindow
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.n0n3m4.mathlib.MathView

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.io.*
import android.widget.FrameLayout
import android.view.View.MeasureSpec
import android.view.ViewGroup


class MainActivity : AppCompatActivity()
{

	val TYPE_LATEX = 0
	val TYPE_USER = 1
	val TYPE_REDUCETEXT = 2

	val handler = Handler()

	val TAG = "ReduceNative"
	var proc: Process? = null;
	var writer: PrintWriter? = null;
	val reducePath: File
		get() = File(applicationInfo.nativeLibraryDir, "libreduce.so");
	val reduceImgPath: File
		get() = File(applicationInfo.nativeLibraryDir, "libreduce.img.so");

	@UiThread
	fun datasetAppend(el: ListElement)
	{
		dataset.add(el)
		reduceOutput.adapter.notifyItemInserted(dataset.size - 1)
		scrollDown()
	}

	var inputsPosted = 0
	@UiThread
	fun postInput(input: String): Boolean
	{
		if ((writer == null) || (input == ""))
			return false
		else
		{
			inputsPosted++
			datasetAppend(ListElement(input, TYPE_USER, inputsPosted))
			writer!!.println(input + (if (!input.endsWith(';')) ";" else ""))
			return true;
		}
	}

	@UiThread
	fun sendCurrentCommand()
	{
		if (postInput(inputCommand.text.toString()))
			inputCommand.setText("")
	}

	@WorkerThread
	fun readerThreadProc(br: BufferedReader)
	{
		var _s: String? = null

		// fancy!-out!-header
		val LINESTART = "\u0002latex:\\black\$\\displaystyle "
		// fancy!-out!-trailer
		val LINEEND = "\$\u0005"

		while ({ _s = br.readLine();_s }() != null)
		{
			val s = _s!!
			Log.v(TAG, s)
			if (s.startsWith(LINESTART) && s.endsWith(LINEEND))
			{
				// This is a Latex output
				val outline = s.substring(LINESTART.length, s.length - LINEEND.length)
				runOnUiThread { datasetAppend(ListElement(outline, TYPE_LATEX, 0)) }
			} else
			{
				// We skip empty lines
				if (s != "")
					runOnUiThread { datasetAppend(ListElement(s, TYPE_REDUCETEXT, 0)) }
			}
		}
	}

	@UiThread
	fun scrollDown()
	{
		reduceOutput.smoothScrollToPosition(reduceOutput.adapter.getItemCount())
	}

	@WorkerThread
	fun startReduce()
	{
		proc = ProcessBuilder(reducePath.absolutePath, "-i", reduceImgPath.absolutePath, "--texmacs").directory(filesDir).start()
		val reader = BufferedReader(InputStreamReader(proc!!.inputStream))
		writer = PrintWriter(proc!!.outputStream, true)
		// Skip empty line
		Log.v(TAG, reader.readLine())
		// Set line length to be 100M, so the line breaking occurs after our app dies completely
		writer!!.println("linelength 100000000;")
		// Skip output of linelength
		Log.v(TAG, reader.readLine())
		// Now start the reader
		Thread { readerThreadProc(reader) }.start()
	}

	fun stopReduce()
	{
		writer!!.println("quit;")
	}

	class ListElement(val value: String, val type: Int, val linenum: Int)

	inner class ReduceOutputAdapter(val dataset: ArrayList<ListElement>) : RecyclerView.Adapter<ReduceOutputAdapter.ViewHolder>()
	{
		inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

		// Create new views (invoked by the layout manager)
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReduceOutputAdapter.ViewHolder
		{
			if (viewType == TYPE_LATEX)
			{
				return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.reduceoutput, parent, false))
			} else if (viewType == TYPE_USER)
			{
				return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.reduceinput, parent, false))
			} else
				return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.reduceoutputtext, parent, false))
		}

		override fun getItemViewType(position: Int): Int
		{
			return dataset[position].type;
		}

		// Replace the contents of a view (invoked by the layout manager)
		override fun onBindViewHolder(holder: ViewHolder, position: Int)
		{
			if (getItemViewType(position) == TYPE_LATEX)
			{
				val mathview = holder.view.findViewById<MathView>(R.id.mathview)
				if (position == dataset.size - 1)
					mathview.listenOnSize {
						if (position != dataset.size - 1)
							mathview.delistenOnSize()
						else
							scrollDown()
					}
				mathview.setDisplayText("\$" + dataset[position].value + "\$")
			} else if (getItemViewType(position) == TYPE_USER)
			{
				(holder.view as LinearLayout).findViewById<AppCompatTextView>(R.id.ri_linenum).text = "${dataset[position].linenum}: "
				(holder.view as LinearLayout).findViewById<AppCompatTextView>(R.id.ri_content).text = dataset[position].value
			} else
			{
				(holder.view as AppCompatTextView).text = dataset[position].value
			}
		}

		// Return the size of your dataset (invoked by the layout manager)
		override fun getItemCount() = dataset.size
	}

	val dataset = ArrayList<ListElement>();

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		Thread {
			startReduce()
		}.start()

		// Can't use XML due to API levels
		reduceOutput.isNestedScrollingEnabled = false
		reduceOutput.layoutManager = LinearLayoutManager(this)
		reduceOutput.adapter = ReduceOutputAdapter(dataset)

		inputCommand.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE)
			{
				sendCurrentCommand();
				true;
			} else
			{
				false;
			}
		}

		inputCommand.setOnKeyListener { _, keyCode, keyEvent ->
			if (keyEvent.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN))
			{
				val list = dataset.filter { x -> x.type == TYPE_USER }.reversed().map { x -> x.value }.distinct()
				var idx = list.indexOf(inputCommand.text.toString())
				if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
					idx += 1
				else
					idx -= 1
				if (idx >= 0 && idx < list.size)
					inputCommand.setText(list[idx])
				true;
			} else if (keyEvent.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER))
			{
				sendCurrentCommand();
				true;
			} else
			{
				false;
			}
		}

		setSupportActionBar(toolbar)

		send_command.setOnClickListener { view ->
			sendCurrentCommand()
		}

		repeat_command.setOnClickListener { _ ->
			val dataz = dataset.filter { x -> x.type == TYPE_USER }.map { x -> x.value }
			if (dataz.size == 0)
				return@setOnClickListener
			val pw = ListPopupWindow(this)
			val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataz)
			pw.width = measureContentWidth(adapter)
			pw.anchorView = repeat_command
			pw.setAdapter(adapter)
			pw.setOnItemClickListener { _, _, position, _ ->
				inputCommand.setText(adapter.getItem(position))
				pw.dismiss()
			}
			pw.show()
			pw.listView!!.setSelection(dataz.size - 1)
		}
	}

	// https://stackoverflow.com/questions/14200724/listpopupwindow-not-obeying-wrap-content-width-spec
	private fun measureContentWidth(listAdapter: ListAdapter): Int
	{
		var mMeasureParent: ViewGroup? = null
		var maxWidth = 0
		var itemView: View? = null
		var itemType = 0

		val widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
		val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
		val count = listAdapter.count
		for (i in 0 until count)
		{
			val positionType = listAdapter.getItemViewType(i)
			if (positionType != itemType)
			{
				itemType = positionType
				itemView = null
			}
			if (mMeasureParent == null)
			{
				mMeasureParent = FrameLayout(this)
			}
			itemView = listAdapter.getView(i, itemView, mMeasureParent)
			itemView!!.measure(widthMeasureSpec, heightMeasureSpec)
			val itemWidth = itemView.measuredWidth
			if (itemWidth > maxWidth)
			{
				maxWidth = itemWidth
			}
		}
		return maxWidth
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		return when (item.itemId)
		{
			else -> super.onOptionsItemSelected(item)
		}
	}

	override fun onDestroy()
	{
		stopReduce()
		super.onDestroy()
	}
}
