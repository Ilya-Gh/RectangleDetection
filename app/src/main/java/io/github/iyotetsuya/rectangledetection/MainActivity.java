package io.github.iyotetsuya.rectangledetection;

import android.Manifest.permission;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import io.github.iyotetsuya.rectangledetection.models.CameraData;
import io.github.iyotetsuya.rectangledetection.models.MatData;
import io.github.iyotetsuya.rectangledetection.utils.OpenCVHelper;
import io.github.iyotetsuya.rectangledetection.views.CameraPreview;
import io.github.iyotetsuya.rectangledetection.views.DrawView;
import org.opencv.android.OpenCVLoader;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.v(TAG, "init OpenCV");
        }
    }

    private static final int PERMISSION_ID = 228;
    private PublishSubject<CameraData> subject = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestCameraPermission();
    }

    private void initCamera() {
        CameraPreview cameraPreview = (CameraPreview) findViewById(R.id.camera_preview);
        cameraPreview.setCallback((data, camera) -> {
            CameraData cameraData = new CameraData();
            cameraData.data = data;
            cameraData.camera = camera;
            subject.onNext(cameraData);
        });
        cameraPreview.setOnClickListener(v -> cameraPreview.focus());
        DrawView drawView = (DrawView) findViewById(R.id.draw_layout);
        subject.concatMap(cameraData ->
                OpenCVHelper.getRgbMat(new MatData(), cameraData.data, cameraData.camera))
                .concatMap(matData -> OpenCVHelper.resize(matData, 400, 400))
                .map(matData -> {
                    matData.resizeRatio =
                            (float) matData.oriMat.height() / matData.resizeMat.height();
                    matData.cameraRatio =
                            (float) cameraPreview.getHeight() / matData.oriMat.height();
                    return matData;
                })
                .concatMap(this::detectRect)
                .compose(mainAsync())
                .subscribe(matData -> {
                    if (drawView != null) {
                        if (matData.cameraPath != null) {
                            drawView.setPath(matData.cameraPath);
                        } else {
                            drawView.setPath(null);
                        }
                        drawView.invalidate();
                    }
                });
    }

    private Observable<MatData> detectRect(MatData mataData) {
        return Observable.just(mataData)
                .concatMap(OpenCVHelper::getMonochromeMat)
                .concatMap(OpenCVHelper::getContoursMat)
                .concatMap(OpenCVHelper::getPath);
    }

    private static <T> Observable.Transformer<T, T> mainAsync() {
        return obs -> obs.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ID: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initCamera();
                } else {

                    finish();
                }
            }
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission.CAMERA)) {
                ActivityCompat.requestPermissions(this, new String[] { permission.CAMERA }, 0);
            } else {
                initCamera();
            }
        }
    }

}
