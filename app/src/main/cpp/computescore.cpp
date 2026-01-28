#include <jni.h>
#include "brisque.h"
#include "libsvm/svm.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#define LOG_TAG "NativeCodeCompute"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

float computescore(string imagename, AAssetManager* assetManager, String modelPath, String savedPath,String basePath,
                   jint x1,
                   jint x2,
                   jint x3,
                   jint x4,
                   jint y1,
                   jint y2,
                   jint y3,
                   jint y4
) {

  // Pre-loaded vectors from allrange file
  float min_[36] = {0.336999, 0.019667, 0.230000, -0.125959, 0.000167, 0.000616, 0.231000, -0.125873, 0.000165, 0.000600, 0.241000, -0.128814, 0.000179, 0.000386, 0.243000, -0.133080, 0.000182, 0.000421, 0.436998, 0.016929, 0.247000, -0.200231, 0.000104, 0.000834, 0.257000, -0.200017, 0.000112, 0.000876, 0.257000, -0.155072, 0.000112, 0.000356, 0.258000, -0.154374, 0.000117, 0.000351};

  float max_[36] = {9.999411, 0.807472, 1.644021, 0.202917, 0.712384, 0.468672, 1.644021, 0.169548, 0.713132, 0.467896, 1.553016, 0.101368, 0.687324, 0.533087, 1.554016, 0.101000, 0.689177, 0.533133, 3.639918, 0.800955, 1.096995, 0.175286, 0.755547, 0.399270, 1.095995, 0.155928, 0.751488, 0.402398, 1.041992, 0.093209, 0.623516, 0.532925, 1.042992, 0.093714, 0.621958, 0.534484};

  double qualityscore;
  int i;
  struct svm_model* model; // create svm model object

  // Read image (color mode)
  LOGI("Reading image: %s", imagename.c_str());
  Mat orig = imread(imagename, 1);
  std::vector<cv::Point2f> srcPoints;

  srcPoints.push_back(cv::Point2f(x1, y1)); // top-left corner
  srcPoints.push_back(cv::Point2f(x2, y2)); // top-right corner
  srcPoints.push_back(cv::Point2f(x3, y3)); // bottom-right corner
  srcPoints.push_back(cv::Point2f(x4, y4)); // bottom-left corner

  // Define the destination points (points in the output image)
  std::vector<cv::Point2f> dstPoints;
  dstPoints.push_back(cv::Point2f(144, 144)); // top-left corner
  dstPoints.push_back(cv::Point2f(724, 144)); // top-right corner
  dstPoints.push_back(cv::Point2f(724, 724)); // bottom-right corner
  dstPoints.push_back(cv::Point2f(144, 724)); // bottom-left corner

  // Compute the perspective transform matrix
  cv::Mat perspectiveMatrix = cv::getPerspectiveTransform(srcPoints, dstPoints);

  // Perform the perspective transformation
  cv::Mat correctedImage;
  cv::warpPerspective(orig, correctedImage, perspectiveMatrix, cv::Size(870, 870));

  cv::imwrite(savedPath, correctedImage);

//  Crop the image to [70:800, 70:800] (x: 70 to 800, y: 70 to 800)
  cv::Rect cropRegion(70, 70, 730, 730);  // width and height are 800 - 70 = 730
  cv::Mat croppedImg = correctedImage(cropRegion);

  // Set the region [75:655, 75:655] within the cropped image to white (255)
  cv::Rect innerRegion(75, 75, 580, 580);  // width and height are 655 - 75 = 580
  cv::Mat submat = croppedImg(innerRegion);
  submat.setTo(cv::Scalar(255, 255, 255));  // For a grayscale image use cv::Scalar(255)

  std::vector<int> compression_params;
  compression_params.push_back(cv::IMWRITE_WEBP_QUALITY);
  compression_params.push_back(50); // Set quality to 50%

  // Write the image to WebP format
//  bool success = cv::imwrite(output_image_path, image, compression_params);

  // Save the modified image (optional)
  cv::imwrite(basePath, croppedImg,compression_params);

  if (correctedImage.empty()) {
    LOGE("Error: Unable to open image: %s", imagename.c_str());
    return -1.0;
  }

  // Feature vector initialization
  vector<double> brisqueFeatures;
  LOGI("Computing BRISQUE features...");
  ComputeBrisqueFeature(correctedImage, brisqueFeatures);
  LOGI("BRISQUE features computed.");

  // Use the pre-trained allmodel file
//  const char* modelfile = "file:///android_asset/allmodel";
  LOGI("Loading SVM model: %s", modelPath.c_str());
  if ((model = svm_load_model(modelPath.c_str(), assetManager)) == 0) {
    LOGE("Can't open model file: %s", modelPath.c_str());
    exit(1);
  }
  LOGI("SVM model loaded.");

  struct svm_node x[37];
  // Rescale the brisqueFeatures vector from -1 to 1
  // Also convert vector to svm node array object
  for (i = 0; i < 36; ++i) {
    float min = min_[i];
    float max = max_[i];
    x[i].value = -1 + (2.0 / (max - min) * (brisqueFeatures[i] - min));
    x[i].index = i + 1;
  }
  x[36].index = -1;

  int nr_class = svm_get_nr_class(model);
  LOGI("nr class: ",nr_class);
  double *prob_estimates = (double *)malloc(nr_class * sizeof(double));

  // Predict quality score using libsvm class
  LOGI("Predicting quality score using SVM model...");
  qualityscore = svm_predict_probability(model, x, prob_estimates);
  LOGI("Quality score predicted: %f", qualityscore);

//  free(prob_estimates);
  svm_free_and_destroy_model(&model);
  return qualityscore;
}

//float computescore(string imagename, AAssetManager* assetManager) {
//  // pre-loaded vectors from allrange file
//  float min_[36] = {0.336999 ,0.019667 ,0.230000 ,-0.125959 ,0.000167 ,0.000616 ,0.231000 ,-0.125873 ,0.000165 ,0.000600 ,0.241000 ,-0.128814 ,0.000179 ,0.000386 ,0.243000 ,-0.133080 ,0.000182 ,0.000421 ,0.436998 ,0.016929 ,0.247000 ,-0.200231 ,0.000104 ,0.000834 ,0.257000 ,-0.200017 ,0.000112 ,0.000876 ,0.257000 ,-0.155072 ,0.000112 ,0.000356 ,0.258000 ,-0.154374 ,0.000117 ,0.000351};
//
//  float max_[36] = {9.999411, 0.807472, 1.644021, 0.202917, 0.712384, 0.468672, 1.644021, 0.169548, 0.713132, 0.467896, 1.553016, 0.101368, 0.687324, 0.533087, 1.554016, 0.101000, 0.689177, 0.533133, 3.639918, 0.800955, 1.096995, 0.175286, 0.755547, 0.399270, 1.095995, 0.155928, 0.751488, 0.402398, 1.041992, 0.093209, 0.623516, 0.532925, 1.042992, 0.093714, 0.621958, 0.534484};
//
//  double qualityscore;
//  int i;
//  struct svm_model* model; // create svm model object
//  Mat orig = imread(imagename, 1); // read image (color mode)
//
//  vector<double> brisqueFeatures; // feature vector initialization
//  ComputeBrisqueFeature(orig, brisqueFeatures); // compute brisque features
//
//  // use the pre-trained allmodel file
//
//  const char* modelfile = "allmodel";
//    if((model=svm_load_model(modelfile, assetManager))==0) {
//    fprintf(stderr,"can't open model file allmodel\n");
//    exit(1);
//  }
//
//  // float min_[37];
//  // float max_[37];
//
//  struct svm_node x[37];
//  // rescale the brisqueFeatures vector from -1 to 1
//  // also convert vector to svm node array object
//  for(i = 0; i < 36; ++i) {
//    float min = min_[i];
//    float max = max_[i];
//
//    x[i].value = -1 + (2.0/(max - min) * (brisqueFeatures[i] - min));
//    x[i].index = i + 1;
//  }
//  x[36].index = -1;
//
//
//  int nr_class=svm_get_nr_class(model);
//  double *prob_estimates = (double *) malloc(nr_class*sizeof(double));
//  // predict quality score using libsvm class
//  qualityscore = svm_predict_probability(model,x,prob_estimates);
//
//  free(prob_estimates);
//  svm_free_and_destroy_model(&model);
//  return qualityscore;
//}

