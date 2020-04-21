package com.example.ChessPlayerApp.robot_arm.Recognition;

import android.Manifest;
import android.media.Image;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.ChessPlayerApp.R;
import com.example.ChessPlayerApp.chessboardcamera.ImageProcessor;
import com.example.ChessPlayerApp.chessboardcamera.KmeanCluster.Match;
import com.example.ChessPlayerApp.chessboardcamera.ZoomCameraView;
import com.example.ChessPlayerApp.robot_arm.Chess.ChessFragment;
import com.example.ChessPlayerApp.robot_arm.Chess.MoveCalculator;
import com.example.ChessPlayerApp.robot_arm.Chess.TheEngine;
import com.example.ChessPlayerApp.robot_arm.RobotArmControllerActivity;
import com.google.android.material.snackbar.Snackbar;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import static com.example.ChessPlayerApp.robot_arm.Chess.ChessFragment.getNextMove;
import static com.example.ChessPlayerApp.robot_arm.Chess.TheEngine.terminal;
import static com.example.ChessPlayerApp.robot_arm.Chess.TheEngine.whiteTurn;
import static com.example.ChessPlayerApp.robot_arm.Chess.TheUserInterface.drawBoardPieces;


public class CameraFragment extends Fragment implements  CameraBridgeViewBase.CvCameraViewListener2{

    enum Mode {
        Normal,
        CalibrateChessBoard,
        MonitorGame,
        DetectMove,
        Contours,
        MatchChessBoard
    }

    private static CameraFragment instance;

    // camera
    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    public static ZoomCameraView mZoomCameraView;

    public final static char W = 'w';
    public final static char B = 'b';
    public final static char E = '*';

    // Used in Camera selection from menu (when implemented)
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    private Mat currImg = null;

    Mode ImgProMethod = Mode.MonitorGame;

    Button caliBtn;
    Button moveBtn;
    Button gameModeButton;
    Button matchModeButton;

    boolean calibrated = false;
    boolean firstClicked_cali = false;
    boolean firstClicked_move = false;

    public static int[][] cali_Intensities = new int[8][8];
    public static int[][] cali_Edges = new int[8][8];

    int[][] currIntensities = null;
    int[][] currEdges = null;


    // localtion of white piece and black piece
    public static char[][] PCL = new char[][]{
            {B,B,B,B,B,B,B,B},
            {B,B,B,B,B,B,B,B},
            {E,E,E,E,E,E,E,E},
            {E,E,E,E,E,E,E,E},
            {E,E,E,E,E,E,E,E},
            {E,E,E,E,E,E,E,E},
            {W,W,W,W,W,W,W,W},
            {W,W,W,W,W,W,W,W}
    };
    public static char[][] lastPcl;
    public char[][] currPcl;


    public static CameraFragment getInstance(){
        if (instance == null)
            instance = new CameraFragment();

        return instance;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);


        View root = inflater.inflate(R.layout.fragment_camera, container, false);

        mZoomCameraView = root.findViewById(R.id.ZoomCameraView);
        mZoomCameraView.setVisibility(SurfaceView.VISIBLE);

        //mZoomCameraView.setCameraIndex(0);
        //mOpenCvCameraView.setScaleX(0.5f);
        //mOpenCvCameraView.setScaleY(0.5f);
        /*
        mZoomCameraView.setZoomControl(findViewById(R.id.CameraZoomControls));
        mZoomCameraView.setCvCameraViewListener(this);
         */
        mZoomCameraView.setCvCameraViewListener(this);
        mZoomCameraView.enableFpsMeter();
        mZoomCameraView.enableView();

        gameModeButton = root.findViewById(R.id.btn_1);
        gameModeButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                ImgProMethod = Mode.MonitorGame;
            }
        });

        matchModeButton = root.findViewById(R.id.btn_2);
        matchModeButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                ImgProMethod = Mode.MatchChessBoard;
            }
        });


        // deactivate when game start
        caliBtn = root.findViewById(R.id.btn_3);
        caliBtn.setOnClickListener(new View.OnClickListener() {
            // Need Click two times, first time: open this mode, second time capture to send to ChessFragment, then return to normal mode
            // This mode can only be used before game start, otherwise after the board occupied, the calibration gives wrong reference data of empty board.
            @Override
            public void onClick(View v) {
                if (!calibrated){
                    if(currEdges != null && currIntensities != null){
                        cali_Edges = currEdges;
                        cali_Intensities = currIntensities;
                        calibrated = true;
                    }
                }
                /*
                if(!firstClicked_cali){
                    firstClicked_cali = true;
                    ImgProMethod = Mode.CalibrateChessBoard;
                }else{
                    // calibrate mode
                    // Do calibration, only after you see the matchBoard, edges no and intensity are well shown can you click for the second time.
                    // Boolean Calibrate(Mat currImg)
                    if (calibrated) {
                        ImgProMethod = Mode.Normal;
                        firstClicked_cali = false;
                    }
                }

                 */
            }
        });

        moveBtn = root.findViewById(R.id.btn_4);
        moveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // if game start, white turn,
                if (TheEngine.whiteTurn && TheEngine.gameStarted){
                    // check pcl
                    String myMove = MoveCalculator.getMove(lastPcl, currPcl);
                    if (myMove != "" && validMove(myMove)){
                        String query = terminal("myMove,"+myMove);
                        lastPcl = currPcl;
                        AIWork();
                    }else{
                        Log.d("MyMove", "Not a valid move, please try again.");
                    }
                }
            }
        });

        return root;
    }

    public void AIWork(){
        drawBoardPieces();
        ChessFragment.wTurn = !ChessFragment.wTurn;


        // Since we moved, if it is not pass and play, make the computer movePiece.
        String moveOptions="";
        if (!ChessFragment.wTurn){
            moveOptions= terminal("suggestMove,black");
        } else {
            moveOptions= terminal("suggestMove,white");
        }
        if (moveOptions.isEmpty()) {
            //ChessFragment.staleOrCheckMate();
            // define camera friendly game end effect
            TheEngine.gameStarted = false;
        } else {
            getNextMove();
            ChessFragment.wTurn = !ChessFragment.wTurn;

        }

        // update PCL from camera or theEngine?
        updatePcl(lastPcl, TheEngine.theBoard);

    }

    void updatePcl(char[][] thePcl, char[] theBoard){
        for(int k = 0; k < 64; k ++){
            int i = 7 - k/8;
            int j = k%8;
            if(theBoard[k] == '*')
                thePcl[i][j] = E;
            else if(Character.isLowerCase(theBoard[k]))
                thePcl[i][j] = B;
            else
                thePcl[i][j] = W;
        }
    }

    boolean validMove(String myMove){
        String moveOptions= terminal("availMoves,"+String.valueOf(TheEngine.whiteTurn));
        String[] separated = moveOptions.split(",");
        if (Arrays.asList(separated).contains(myMove)) {
            return true;
        }else{
            return false;
        }
    }

    public Mat GameMonitor(Mat src){
        // get transformMat and chessboard points
        List<List<Point>> boardPoints = ImageProcessor.getIntersetPoints(src);
        Match pointsAndTranf = ImageProcessor.getChessBoardPointsAndMatrix(boardPoints);
        // get intensifies and edges
        if (pointsAndTranf != null){
            Mat AOI = ImageProcessor.tranformInterestArea(src, pointsAndTranf.tranf);
            int[][] currIntensities = ImageProcessor.getIntensity(AOI);
            int[][] currEdges = ImageProcessor.getEdges(AOI);
            src = ImageProcessor.drawPoint(src, pointsAndTranf.points);

            //cali_Intensities = intensities;
            //cali_Edges = edges;
            currPcl = ImageProcessor.getPieceColor(currEdges, cali_Edges, currIntensities, cali_Intensities);

            if (calibrated && whiteTurn){
                // only calibrated can you do:
                //currPcl = ImageProcessor.getPieceColor(edges, cali_Edges, intensities, cali_Intensities);
            }else if(calibrated && !whiteTurn){
                // no need to update lastPcl, since it has been updated from theBoard in TheEngine.java
            }

            // Show currPcl on mat


            /*
            for(int i = 0; i < 8; i ++)
                for(int j = 0; j < 8; j ++){
                    //Imgproc.putText(src, " E: "+ edges[i][j] + "\n I: " + (int)intensities[i][j], pointsAndTranf.points[i][j], Core.FONT_HERSHEY_COMPLEX, 3, new Scalar(255,0, 0,255), 4);
                    //Log.d("Info", " E: "+ edges[i][j] + ", I: " + intensities[i][j]);
                }

             */
            // put data in original mat
            //Imgproc.putText(chessboardMat, (int)mu + "", new Point(start.x,start.y), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(150,255, 23));

        }


        return src;
    }





    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();
        Mat mRgbaT = mRgba.t();
        Core.flip(mRgba.t(), mRgbaT, 1);
        Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());

        Mat processed = null;

        switch (ImgProMethod){
            case MonitorGame:
                processed = GameMonitor(mRgbaT);
                break;

            case MatchChessBoard:
                processed  = ImageProcessor.MatchChessboard(mRgbaT);
                break;

            case Contours:
                processed  = ImageProcessor.Contours(mRgbaT);
                break;
        }

        //Log.d("CameraTest", "It capture frame.");
        currImg = processed;

        return processed;
    }



}