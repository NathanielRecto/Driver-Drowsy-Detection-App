# Real-Time Driver Drowsiness Detection and Alert System

Android-based capstone project that detects driver drowsiness using computer vision, facial landmark analysis, and lightweight on-device machine learning.

This project was developed for the **Computer/Electrical Engineering Capstone Design Project (NG05)** at **Toronto Metropolitan University**.

## Team Members

- Nathaniel Recto
- Thanh Khoa Nguyen
- Sarim Aamir
- Benjamin Tan

## Demo

<!-- Recommended image: README_assets/demo.gif -->
![Demo of the Android drowsiness detection app](README_assets/demo.gif)

The app uses the Android front-facing camera to monitor driver behaviour in real time. It detects fatigue-related cues such as eye closure, yawning, and head tilt. When the combined drowsiness score reaches the alert threshold, the app changes the driver status and triggers an alarm.

## Motivation

Driver fatigue is a road-safety issue because it reduces awareness, slows reaction time, and increases collision risk. Traditional approaches such as lane tracking, steering behaviour, or wearable sensors can be delayed, intrusive, or impractical for daily use. This project uses a non-intrusive camera-based approach that can run directly on an Android phone.

## Custom Dataset

<!-- Recommended image: README_assets/dataset_samples.png -->
![Custom dataset samples](README_assets/dataset_samples.png)

A custom MediaPipe-based data collection tool was created to crop and label eye and mouth regions from live camera input. The dataset was organized into four class folders:

- `eyes_open`
- `eyes_closed`
- `mouth_normal`
- `mouth_yawn`

These cropped images were used to train two separate MobileNetV2-based classifiers:

- Eye-state model: `eyes_open` vs. `eyes_closed`
- Yawn model: `mouth_normal` vs. `mouth_yawn`

Final dataset size:

| Dataset Class | Number of Images |
|---|---:|
| Eyes Closed | 3,795 |
| Eyes Open | 4,612 |
| Mouth Normal | 4,766 |
| Mouth Yawn | 2,828 |

## How It Works

<!-- Recommended image: README_assets/system_pipeline.png -->
![System pipeline](README_assets/system_pipeline.png)

The system uses a hybrid detection pipeline:

1. **Camera Input**  
   The Android front-facing camera captures live video frames.

2. **Facial Landmark Detection**  
   MediaPipe Face Landmarker detects facial landmarks from each frame.

3. **Feature Extraction**  
   The app calculates Eye Aspect Ratio (EAR), Mouth Aspect Ratio (MAR), and head tilt.

4. **Machine Learning Inference**  
   Cropped eye and mouth regions are passed into two TensorFlow Lite MobileNetV2 models.

5. **Alert Logic**  
   A weighted scoring system combines eye-closure duration, model output, yawn rate, and head-tilt duration. An alert is triggered when the drowsiness score reaches 100.

## App Screens

<!-- Recommended image: README_assets/app_screens.png -->
![Android app screens](README_assets/app_screens.png)

The Android application includes a start screen, real-time camera screen, head tilt calibration, drowsy alert display, and session analytics screen. The session screen records frames processed, eye-closed duration, head-tilt duration, blink count, yawn count, and drowsy events.

## Performance Summary

The two MobileNetV2-based models were trained using the custom cropped dataset and converted to TensorFlow Lite for Android deployment.

| Model | Accuracy | Precision | Recall |
|---|---:|---:|---:|
| Eye-State Model | 99.17% | 0.99 | 0.98 |
| Yawn Detection Model | 96.18% | 0.99 | 0.90 |

<!-- Recommended image: README_assets/confusion_matrices.png -->
![Model confusion matrices](README_assets/confusion_matrices.png)

The full Android system was tested using four behaviour cases across four operating conditions.

| Condition | Eye Closure | Single Yawn | Head Tilt | Combined Eye Closure, Yawn, and Head Tilt |
|---|---:|---:|---:|---:|
| Normal lighting, no glasses | 100% | 100% | 100% | 100% |
| Normal lighting, with glasses | 70% | 100% | 100% | 100% |
| Dim lighting, no glasses | 70% | 90% | 100% | 100% |
| Dim lighting, with glasses | 60% | 90% | 100% | 100% |

Main findings:

- Best performance occurred under normal lighting without glasses.
- Head tilt was the most consistent cue across all tested conditions.
- Eye-closure detection became less reliable when glasses and lens reflections were present.
- Dim lighting reduced the stability of EAR and MAR feature measurements.
- The multi-cue scoring system helped the final alert decision remain stable when one cue became less reliable.

## Limitations

Testing showed that eye-state detection became less reliable when glasses caused reflections over the eye region. Dim lighting also reduced the stability of EAR and MAR feature measurements. More testing across users, phone placements, and real driving conditions would be needed before practical deployment.

## Tech Stack

- Kotlin
- Android Studio
- CameraX
- MediaPipe Face Landmarker
- TensorFlow Lite
- TensorFlow / Keras
- MobileNetV2
- Python

## Future Improvements

- Improve eye-state detection for users wearing glasses
- Add low-light image enhancement
- Expand the training dataset
- Add personalized threshold calibration
- Improve blink-rate tracking
- Test with more users and driving environments

## Project Status

Completed as a capstone engineering prototype. The project demonstrates real-time Android-based drowsiness monitoring using facial landmark analysis, lightweight deep learning, and multi-cue alert logic.
