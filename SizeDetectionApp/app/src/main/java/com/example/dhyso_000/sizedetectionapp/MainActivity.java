package com.example.dhyso_000.sizedetectionapp;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, View.OnClickListener  {

    private UsbService usbService;
    private TextView txtDistance;
    private TextView txtMeasurement;
    private MyHandler mHandler;

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat orig;
    private Mat mRgba;
    private Mat edges;
    private Mat mGray;
    private Mat img;
    private List<MatOfPoint> contours;
    private double[] previousDistances = new double[] {-99, -99, -99};
    private Button btnMeasure;
    private Button btnReset;
    private Button btnCalibrate;
    private boolean shouldProcess = false;
    private Intent intent;
    private double sensorWidth = 34;
    private double sensorHeight = 19;
    private int minWidth = 100;
    private int minHeight = 100;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("Message", "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mHandler = new MyHandler(this);

        txtDistance = (TextView) findViewById(R.id.textView1);
        txtMeasurement = (TextView) findViewById(R.id.textView5);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.image_manipulations_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        btnMeasure = (Button)findViewById(R.id.button);
        btnMeasure.setOnClickListener(MainActivity.this);

        btnReset = (Button)findViewById(R.id.button2);
        btnReset.setOnClickListener(MainActivity.this);

        btnCalibrate = (Button)findViewById(R.id.button4);
        btnCalibrate.setOnClickListener(MainActivity.this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                if (mOpenCvCameraView != null)
                    shouldProcess = true;

                btnMeasure.setVisibility(View.INVISIBLE);
                btnReset.setVisibility(View.VISIBLE);
                break;
            case R.id.button2:
                if (mOpenCvCameraView != null) {
                    shouldProcess = false;
                    mOpenCvCameraView.enableView();
                }
                
                txtMeasurement.setText("");

                btnMeasure.setVisibility(View.VISIBLE);
                btnReset.setVisibility(View.INVISIBLE);
                break;
            case R.id.button4:
                intent = new Intent(MainActivity.this, CalibrateActivity.class);
                intent.putExtra("sensorWidth", sensorWidth);
                intent.putExtra("sensorHeight", sensorHeight);
                intent.putExtra("minWidth", minWidth);
                intent.putExtra("minHeight", minHeight);
                startActivityForResult(intent, 0);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Retrieve data in the intent
        Bundle extra = data.getExtras();
        sensorWidth = extra.getDouble("sensorWidth");
        sensorHeight = extra.getDouble("sensorHeight");
        minWidth = extra.getInt("minWidth");
        minHeight = extra.getInt("minHeight");
    }


    @Override
    public void onResume() {
        super.onResume();

        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it

        if (!OpenCVLoader.initDebug()) {
            Log.d("Message", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mLoaderCallback);
        } else {
            Log.d("Message", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        orig = new Mat(height, width, CvType.CV_8UC4);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        edges = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        img = new Mat();
    }

    public void onCameraViewStopped() {
        orig.release();
        mRgba.release();
        mGray.release();
        edges.release();
        img.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        orig = inputFrame.rgba();
        mRgba = inputFrame.gray();

        contours = new ArrayList<MatOfPoint>();
        img = new Mat();

        Imgproc.Canny(mRgba, edges, 50, 200);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7,7), new Point(1,1));

        Mat closed = new Mat();

        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel);

        Imgproc.findContours(edges.clone(), contours, img, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        img.release();

        for( int i = 0; i< contours.size(); i++ )
        {
            MatOfPoint2f contour = new MatOfPoint2f( contours.get(i).toArray() );
            double approxDistance = Imgproc.arcLength(contour, true);
            MatOfPoint2f approx2f = new MatOfPoint2f();
            MatOfPoint approx = new MatOfPoint();
            Imgproc.approxPolyDP(contour, approx2f,  0.02 * approxDistance, true);

            approx2f.convertTo(approx, CvType.CV_32S);

            if (approx.size().height == 4) {
                MatOfPoint points = new MatOfPoint( approx.toArray() );

                Rect rect = Imgproc.boundingRect(points);
                boolean isCentered = rect.contains(new Point(orig.width() / 2, orig.height() / 2));

                if(isCentered && rect.height > 100 && rect.width > 100) {
                    Imgproc.drawContours(orig, contours, i, new Scalar(0, 255, 0), 2);

                    double focalLength = 31;
                    double distance = 0;
                    boolean distanceStable = true;
                    try {
                        distance = Double.parseDouble(mHandler.getFullData());
                        double[] newDistances = new double[3];
                        for (int j = 0; i < previousDistances.length; i++) {
                            if (distanceStable && Math.abs(previousDistances[j] - distance) > 5) {
                                distanceStable = false;
                            }
                            if(j > 0) {
                                newDistances[j - 1] = previousDistances[j];
                            }
                        }
                        newDistances[2] = distance;
                    }
                    catch (NumberFormatException e) {
                    }

                    if (shouldProcess && distanceStable && distance > 0) {
                        shouldProcess = false;

                        double objectWidth = ((rect.width * sensorWidth * distance) / (focalLength * orig.width())) / 2.54;
                        double objectHeight = ((rect.height * sensorHeight * distance) / (focalLength * orig.height())) / 2.54;

                        /*int leftPadding = 20;
                        int topPadding = 20;
                        Core.putText(orig, String.format("height (px): %1$d", rect.height), new Point(rect.x + leftPadding, rect.y + 25 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("width (px): %1$d", rect.width), new Point(rect.x + leftPadding, rect.y + 50 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("height (in): %1$f", objectHeight), new Point(rect.x + leftPadding, rect.y + 75 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("width (in): %1$f", objectWidth), new Point(rect.x + leftPadding, rect.y + 100 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("distance (in): %1$f", distance / 2.54), new Point(rect.x + leftPadding, rect.y + 125 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("img height (px): %1$d", orig.height()), new Point(rect.x + leftPadding, rect.y + 150 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);
                        Core.putText(orig, String.format("img width (px): %1$d", orig.width()), new Point(rect.x + leftPadding, rect.y + 175 + topPadding), 3, 1, new Scalar(0, 255, 0, 255), 1);*/


                        StopImageProcessing(objectWidth, objectHeight);
                    }

                }
            }
        }

        return orig;
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }


    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        private String partialData = "";
        private String fullData = "0";

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public String getFullData(){
            return fullData;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;

                    int lfIndex = data.indexOf("\n");
                    if(lfIndex > -1) {
                        fullData = partialData + data.substring(0, lfIndex);
                        mActivity.get().txtDistance.setText("Current Distance: " + fullData + " (in)");
                        partialData = data.substring(lfIndex, data.length());
                    }
                    else {
                        partialData += data;
                    }
                    break;
            }
        }
    }

    private void StopImageProcessing(final double objectWidth, final double objectHeight) {
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                if (mOpenCvCameraView != null)
                    mOpenCvCameraView.disableView();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtMeasurement.setText(String.format("width (in): %1$f%nheight (in): %2$f", objectWidth, objectHeight));
                    }
                });

            }
        });

        t.start();
    }

}
