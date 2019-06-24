package de.kai_morich.simple_bluetooth_le_terminal

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import kotlinx.android.synthetic.main.fragment_terminal.*



import java.util.Date

class TerminalFragment : Fragment(), ServiceConnection, SerialListener {

    private var deviceAddress: String? = null

    private var newline = "\r\n"

    private var receiveText: TextView? = null

    private var socket: SerialSocket? = null
    private var service: SerialService? = null

    private var initialStart = true
    private var connected = Connected.False

    lateinit var seekbar1: SeekBar
    lateinit var seekbar2: SeekBar
    lateinit var seekbar3: SeekBar

    lateinit var value_LeftLight: TextView
    lateinit var value_RightLight: TextView
    lateinit var value_BackLight: TextView

    private var value_Ideal_LeftLight = 170
    private var value_Ideal_RightLight = 120
    private var value_Ideal_BackLight = 170


    private enum class Connected {
        False, Pending, True
    }

    /*
     * Lifecycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
        retainInstance = true
        deviceAddress = arguments!!.getString("device")

    }

    override fun onDestroy() {
        if (connected != Connected.False)
            disconnect()
        activity!!.stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (service != null)
            service!!.attach(this)
        else
            activity!!.startService(Intent(activity, SerialService::class.java)) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    override fun onStop() {
        if (service != null && !activity!!.isChangingConfigurations)
            service!!.detach()
        super.onStop()
    }

    override// onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        getActivity()!!.bindService(Intent(getActivity(), SerialService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onDetach() {
        try {
            activity!!.unbindService(this)
        } catch (ignored: Exception) {
        }

        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        if (initialStart && service != null) {
            initialStart = false
            activity!!.runOnUiThread { this.connect() }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialService.SerialBinder).service
        if (initialStart && isResumed) {
            initialStart = false
            activity!!.runOnUiThread { this.connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    /*
     * UI
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)

        //init sliders with listeners
        seekbar1 = view.findViewById(R.id.slider1)
        seekbar2 = view.findViewById(R.id.slider2)
        seekbar3 = view.findViewById(R.id.slider3)

        value_LeftLight = view.findViewById(R.id.text1)
        value_RightLight = view.findViewById(R.id.text2)
        value_BackLight = view.findViewById(R.id.text3)

        seekbar1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
               result = format(progress)
                value_LeftLight.text = "Führung" + newline + "Helligkeit: " + result
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${result}${format(seekbar2.progress)}${format(seekbar3.progress)}")

            }
        })

        seekbar2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                value_RightLight.text = "Aufhellung" + newline + "Helligkeit: " + result

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar1.progress)}${result}${format(seekbar3.progress)}")

            }
        })

        seekbar3.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                value_BackLight.text = "Spitze" + newline + "Helligkeit: " + result
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar1.progress)}${format(seekbar2.progress)}${result}")
            }
        })

        val sendKeyword = "setalight"
        val sendBtn = view.findViewById<View>(R.id.button_init)
        sendBtn.setOnClickListener { v -> send(sendKeyword) }
        return view!!
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.menu_terminal, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item!!.itemId
        if (id == R.id.clear) {
            receiveText!!.text = ""
            return true
        } else if (id == R.id.newline) {
            val newlineNames = resources.getStringArray(R.array.newline_names)
            val newlineValues = resources.getStringArray(R.array.newline_values)
            val pos = java.util.Arrays.asList(*newlineValues).indexOf(newline)
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Newline")
            builder.setSingleChoiceItems(newlineNames, pos) { dialog, item1 ->
                newline = newlineValues[item1]
                dialog.dismiss()
            }
            builder.create().show()
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    private fun format(input: Int):String{
        if(input < 10){
            return "00${input}"
        }else if(input < 100){
            return "0${input}"
        }else {
            return "${input}"
        }
    }
    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            val deviceName = if (device.name != null) device.name else device.address
            status("connecting...")
            connected = Connected.Pending
            socket = SerialSocket()
            service!!.connect(this, "Connected to $deviceName")
            context?.let { socket!!.connect(it, service!!, device) }
        } catch (e: Exception) {
            onSerialConnectError(e)
        }

    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
        socket!!.disconnect()
        socket = null
    }

    private fun send(str: String) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val data = str.toByteArray()
            socket!!.write(data)
        } catch (e: Exception) {
            onSerialIoError(e)
        }

    }
    private fun calculateBrightness(input: List<String>):String{
        //todo culcate every new value

        //todo set sliders to the new values
        return "${format(80)}${format(60)}${format(100)}"
    }
    private fun receive(data: ByteArray) {
        val s = String(data).chunked(3)

        //set values from arduino to the sliders
        seekbar1.setProgress(s[0].toInt())
        seekbar2.setProgress(s[1].toInt())
        seekbar3.setProgress(s[2].toInt())

        //TODO calculate new brightness values from received data
        send(calculateBrightness(s))

    }

    private fun status(str: String) {
        val spn = SpannableStringBuilder(str + '\n')
        spn.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorStatusText)), 0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
       // receiveText!!.append(spn)
    }

    /*
     * receiveText
     */
    override fun onSerialConnect() {
        status("connected")
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        receive(data)
    }

    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }

}
