package me.wcy.music.utils;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.FILLED;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_NONE;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2YCrCb;
import static org.opencv.imgproc.Imgproc.MORPH_OPEN;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.RETR_EXTERNAL;

/**
 * Created by 袁帅 on 2018/4/2.
 */

public class Locus {
    Point newPoint;
    List<Point> tracks=new ArrayList<Point>();
    Size winSize=new Size(200,200);
    static  int frameBuffer = 10;
    static  int volStep = 20;
    public  enum Gesture{NOOPERATION,PAUSEPLAY,NEXT,PREVIOUS};

    public Locus(){
        newPoint=new Point(-1,-1);
        //winSize=win;
    }
    private boolean InRegionLeftOfTurn(Point p, Size winSize){
        if (p.x>0&&p.x < winSize.width / 4){
            return true;
        }
        return false;
    }
    private boolean InRegionRightOfTurn(Point p, Size winSize){
        if (p.x>winSize.width * 3 / 4){
            return true;
        }
        return false;
    }
    private boolean InRegionTop(Point p, Size winSize){
        if (p.y>0&&p.y < winSize.height  / 4){
            return true;
        }
        return false;
    }
    private boolean InRegionBottom(Point p, Size winSize){
        if (p.y > winSize.height * 3 / 4){
            return true;
        }
        return false;
    }

    public Gesture analyseLocus(){
        Gesture g= Gesture.NOOPERATION;
        Point first=tracks.get(0);
        if (InRegionRightOfTurn(first, winSize) && InRegionLeftOfTurn(newPoint, winSize)){
            g = Gesture.PAUSEPLAY;
            tracks.clear();
            tracks.add(newPoint);
        }
        /*
        else if (InRegionLeftOfTurn(first, winSize) && InRegionRightOfTurn(newPoint, winSize)){
            g = Gesture.PAUSEPLAY;
            tracks.clear();
            tracks.add(newPoint);
        }*/
        else if (InRegionTop(first, winSize) && InRegionBottom(newPoint, winSize)){
            g = Gesture.NEXT;
            tracks.clear();
            tracks.add(newPoint);
        }
        else if (InRegionBottom(first, winSize) && InRegionTop(newPoint, winSize)){
            g = Gesture.PREVIOUS;
            tracks.clear();
            tracks.add(newPoint);
        }


        return g;

    }

    public void addPoint(Point p, Size winSize){
        tracks.add(p);
        newPoint=p;
    }
    public void reset(){
        tracks.clear();
        newPoint=new Point(-1,-1);
    }
    public void setWinSize(Size win){
        winSize=win;
    }
    /**
     *
     * @param ImgFrame
     * @param threshold_lower
     * @param threshold_higher
     * @param mats:{cb,ImgFrameSmall}
     * @param winSize
     */
    public static void FramePreHandle(Mat ImgFrame, int  threshold_lower, int  threshold_higher, List<Mat> mats, Size winSize){
        long startTime = System.nanoTime();  //開始時間
        Mat ImgSkin=new Mat();
        Mat cb;
        Mat ImgFrameSmall=mats.get(1);
        List<Mat> channels=new java.util.ArrayList<Mat>(3);
        Imgproc.resize(ImgFrame, ImgFrameSmall, winSize);
        Imgproc.cvtColor(ImgFrameSmall, ImgSkin,COLOR_RGB2YCrCb);
        //Mat temp=ImgSkin.clone();
        /*-------------------version-1----------处理时间过长，废弃-----------------
        double[] data;

        for(int i=0;i<temp.rows();i++){
            for(int j=0;j<temp.cols();j++) {
                data = temp.get(i, j);
                if(data[2]<=122&&data[2]>=0)
                    data[2]=255;
                else
                    data[2]=0;
                ImgSkin.put(i,j,data);
            }
        }
        ---------------------------------------------------------------------------*/


        Core.split(ImgSkin,channels);
        channels.get(1).convertTo(channels.get(1), CvType.CV_32S);
        //channels.get(1).convertTo(channels.get(1),CV_32S);//Cr通道



        int size = (int) (channels.get(1).total() * channels.get(1).channels());
        //int[] Cr=new int [size];

        int[] temp = new int[size];
        //channels.get(1).get(0,0,Cr);
        channels.get(1).get(0,0,temp);

        for(int i=0;i<size;i++){
            if(temp[i]<=160&&temp[i]>=140){
                temp[i]=255;
            }
            else{
                temp[i]=0;
            }
        }
        Mat image1 = new Mat(ImgSkin.size(), CvType.CV_32S);
        image1.put(0,0,temp);
        image1.convertTo(image1,CV_8UC1);
        long consumingTime = System.nanoTime() - startTime; //消耗時間

        Log.i("prehandle时间：",consumingTime/1000000+"毫秒");
        //ThresholdBidirection(channels.get(2), threshold_lower, threshold_higher);

        Mat element = Imgproc.getStructuringElement(MORPH_RECT,new Size(5,5));
        Imgproc.morphologyEx(image1, image1, MORPH_OPEN, element);
        //cb = channels.get(2);
        //cb.size();
        //mats.clear();
        mats.add(image1);
        mats.add(ImgFrameSmall);



    }

    /**
     *
     * @param target_mats{img,filtered}
     * @param erea_threshold
     * @param rectList{target_rect}
     */
    public static void FindTargets(List<Mat> target_mats, int erea_threshold, List<Rect> rectList){

        long startTime = System.nanoTime();  //開始時間

        //   这里是你要测量的代码
        //


        Mat img=target_mats.get(0);
        Mat filtered;
        Rect rect;
        Mat hierarchy=new Mat();
        List<MatOfPoint> contours=new java.util.ArrayList<MatOfPoint>();
        Imgproc.findContours(img, contours,hierarchy,RETR_EXTERNAL, CHAIN_APPROX_NONE);
        MatOfPoint max_contour=new MatOfPoint();
        double max_area = 0;
        for (MatOfPoint item : contours){
            double area = Imgproc.contourArea(item);
            if (area > max_area && area > erea_threshold){
                max_area = area;
                max_contour = item;
            }
        }


        //rect = new Rect();
        //if (max_contour.size() > 0)
        rect = Imgproc.boundingRect(max_contour);
        List<MatOfPoint> newcontour=new ArrayList<MatOfPoint>();
        newcontour.add(max_contour);
        filtered = Mat.zeros(img.rows(), img.cols(), CV_8UC3);
        Imgproc.drawContours(filtered, newcontour, -1, new Scalar(255, 255, 255), FILLED);
        //Imgproc.rectangle(filtered, rect, new Scalar(0, 255, 255), 3);
        Imgproc.rectangle(filtered,rect.tl(),rect.br(),new Scalar(0,255,255),3);
        target_mats.add(filtered);
        rectList.add(rect);

//#ifdef DEBUG
        //imshow("contours", filtered);
//#endif
        long consumingTime = System.nanoTime() - startTime; //消耗時間

        Log.i("findtarget时间：",consumingTime/1000000+"毫秒");
        //System.out.println(consumingTime/1000+"秒");
    }
    void test(List<Mat> a){
        //long startTime = System.nanoTime();  //開始時間
        Mat b=a.get(0);
        Imgproc.cvtColor(b,b,COLOR_RGB2YCrCb);
        Mat c=b.clone();
        /*double[] data;
        for(int i=0;i<b.rows();i++){
            for(int j=0;j<b.cols();j++) {
                data = b.get(i, j);
                if(data[2]<=122&&data[2]>=0)
                    data[2]=255;
                else
                    data[2]=0;
                c.put(i,j,data);
            }
        }*/
        List<Mat> channels=new ArrayList<Mat>(3);
        Core.split(c,channels);
        channels.get(2).convertTo(channels.get(2), CvType.CV_32S);

        int size = (int) (channels.get(2).total() * channels.get(2).channels());

        int[] temp = new int[size];
        channels.get(2).get(0,0,temp);

        for(int i=0;i<size;i++){
            if(temp[i]<=122&&temp[i]>=0){
                temp[i]=255;
            }
            else{
                temp[i]=0;
            }
        }


        //int [] ans=ImgProcess(temp,c.cols(),c.rows());
        Mat image1 = new Mat(c.size(), CvType.CV_32S);
        image1.put(0,0,temp);
        image1.convertTo(image1,CV_8UC1);
        a.add(image1);
        //long consumingTime = System.nanoTime() - startTime; //消耗時間

        //Log.i("test时间：",consumingTime/1000000+"毫秒");
        //return temp;


    }


}
