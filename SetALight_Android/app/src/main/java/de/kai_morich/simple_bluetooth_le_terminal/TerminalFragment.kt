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
import android.support.v4.content.ContextCompat
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

    private var realValue_lamp1 = 0
    private var realValue_lamp2= 0
    private var realValue_lamp3 = 0

    lateinit var seekbar_1_c: SeekBar
    lateinit var seekbar_2_c: SeekBar
    lateinit var seekbar_3_c: SeekBar
    lateinit var seekbar1s: SeekBar
    lateinit var seekbar2s: SeekBar
    lateinit var seekbar3s: SeekBar
    lateinit var seekbar_1_b: SeekBar
    lateinit var seekbar_2_b: SeekBar
    lateinit var seekbar_3_b: SeekBar

    lateinit var shownValue_lamp1_col: TextView
    lateinit var shownValue_lamp1_sat: TextView
    lateinit var shownValue_lamp1_bright: TextView
    lateinit var shownValue_lamp2_col: TextView
    lateinit var shownValue_lamp2_sat: TextView
    lateinit var shownValue_lamp2_bright: TextView
    lateinit var shownValue_lamp3_col: TextView
    lateinit var shownValue_lamp3_sat: TextView
    lateinit var shownValue_lamp3_bright: TextView

    private var idealValue_lamp1 = 230
    private var idealValue_lamp2 = 50
    private var idealValue_lamp3 = 10


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

        // Both Buttons

        val sendKeyword = "setalight"
        val sendBtn = view.findViewById<View>(R.id.button_init)

        val sendSetup_value = "000000010000000010000000010"
        val sendSetup = view.findViewById<View>(R.id.button_Setup)

        sendBtn.setOnClickListener { v ->
            send(sendKeyword)
            //sendBtn.setText("Waiting")
        }
        sendSetup.setOnClickListener{ v ->
            seekbar_1_b.setProgress(10)
            seekbar_2_b.setProgress(10)
            seekbar_3_b.setProgress(10)
            send(sendSetup_value)
        }

        //init sliders with listeners
        seekbar_1_c = view.findViewById(R.id.Lamp1_col)
        seekbar_2_c = view.findViewById(R.id.Lamp2_col)
        seekbar_3_c = view.findViewById(R.id.Lamp3_col)
        seekbar1s = view.findViewById(R.id.Lamp1_sat)
        seekbar2s = view.findViewById(R.id.Lamp2_sat)
        seekbar3s = view.findViewById(R.id.Lamp3_sat)
        seekbar_1_b = view.findViewById(R.id.Lamp1_brightness)
        seekbar_2_b = view.findViewById(R.id.Lamp2_brightness)
        seekbar_3_b = view.findViewById(R.id.Lamp3_brightness)

        shownValue_lamp1_col = view.findViewById(R.id.Lamp1_text_col)
        shownValue_lamp1_sat = view.findViewById(R.id.Lamp1_saturation_text)
        shownValue_lamp1_bright = view.findViewById(R.id.Lamp1_text_bright)
        shownValue_lamp2_col = view.findViewById(R.id.Lamp2_text_col)
        shownValue_lamp2_sat = view.findViewById(R.id.Lamp2_saturation_text)
        shownValue_lamp2_bright = view.findViewById(R.id.Lamp2_text_bright)
        shownValue_lamp3_col = view.findViewById(R.id.Lamp3_text_col)
        shownValue_lamp3_sat = view.findViewById(R.id.Lamp3_saturation_text)
        shownValue_lamp3_bright = view.findViewById(R.id.Lamp3_text_bright)


        // Seekbars that change brightness of lamps

        seekbar_1_c.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp1_col.text = "Farbe: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${result}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar_2_c.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp2_col.text = "Farbe: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${result}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar_3_c.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp3_col.text = "Farbe: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${result}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
            }
        })


        // Seekbars that change saturation of lamps

        seekbar1s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp1_sat.text = "Sättigung: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${result}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar2s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp2_sat.text = "Sättigung: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${result}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar3s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp3_sat.text = "Sättigung: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${result}${format(seekbar_3_b.progress)}")
            }
        })

        // Seekbars that change color of lamps

        seekbar_1_b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp1_bright.text = "Helligkeit: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${result}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar_2_b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp2_bright.text = "Helligkeit: " + result_toPrint

            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${result}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")

            }
        })

        seekbar_3_b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var result = "000"
            var result_toPrint = ""
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                result = format(progress)
                result_toPrint = progress.toString()
                shownValue_lamp3_bright.text = "Helligkeit: " + result_toPrint
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${seekbar_3_c.progress}${format(seekbar3s.progress)}${result}")
            }
        })


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

    // format Seekbar values to String with 3 chars

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

    // calculate ideal brightness for lamps
    private fun calculateBrightness(a: List<String>):String{
       Log.d("receive", "Calculate Brightness Anfang")

        // compares each real value to ideal value and sends new value to lamps
        for (i in a.indices){
            if(i == 0){
                if (a[i].toInt()< (idealValue_lamp1-10) || a[i].toInt()>(idealValue_lamp1+10)){
                    Log.d("receive", "Lampe 1 wird eingestellt")
                    var factor = (idealValue_lamp1/realValue_lamp1).toInt()

                    if (((seekbar_1_b.progress)*factor)>255){
                        seekbar_1_b.setProgress(255)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else if(factor == 0){
                        factor = 1
                        seekbar_1_b.setProgress((seekbar_3_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else{
                        seekbar_1_b.setProgress((seekbar_1_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }

                }
                else{}
            }
            else if (i == 1){
                if (a[i].toInt()< (idealValue_lamp2-10) ||  a[i].toInt()>(idealValue_lamp2+10)){
                    Log.d("receive", "Lampe 2 wird eingestellt")
                    var factor = (idealValue_lamp2/realValue_lamp2).toInt()
                    if (((seekbar_2_b.progress)*factor)>255){
                        seekbar_2_b.setProgress(255)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else if(factor == 0){
                        factor = 1
                        seekbar_2_b.setProgress((seekbar_3_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else{
                        seekbar_2_b.setProgress((seekbar_2_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                }
                else{}

            }
            else if (i == 2){
                Log.d("receive", "Lampe 3 wird eingestellt")
                if (a[i].toInt()< (idealValue_lamp3-10) ||  a[i].toInt()>(idealValue_lamp3+10)){
                    var factor = (idealValue_lamp3/realValue_lamp3).toInt()
                    if (((seekbar_3_b.progress)*factor)>255){
                        seekbar_3_b.setProgress(255)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else if(factor == 0){
                        factor = 1
                        seekbar_3_b.setProgress((seekbar_3_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                    else{

                        seekbar_3_b.setProgress((seekbar_3_b.progress)*factor)
                        send("${format(seekbar_1_c.progress)}${format(seekbar1s.progress)}${format(seekbar_1_b.progress)}${format(seekbar_2_c.progress)}${format(seekbar2s.progress)}${format(seekbar_2_b.progress)}${format(seekbar_3_c.progress)}${format(seekbar3s.progress)}${format(seekbar_3_b.progress)}")
                    }
                }
                else{}

            }

        }

        //todo set sliders to the new values
        return "${format(80)}${format(60)}${format(100)}"
    }
    private fun receive(data: ByteArray) {
        val s = String(data).chunked(3)

        //save values from arduino
        realValue_lamp1 = (s[0].toInt())
        realValue_lamp2= (s[1].toInt())
        realValue_lamp3 = (s[2].toInt())

        Log.d("receive ", realValue_lamp1.toString()+ " " + realValue_lamp2.toString() + " " + realValue_lamp3.toString())

        // calculate ideal value for lamps
        calculateBrightness(s)

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
