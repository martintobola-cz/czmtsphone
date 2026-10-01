package cz.mts.phone.recorder;

interface ICallRecorderService {
    void startRecording(String outputPath);
    void stopRecording();
    boolean isRecording();
    void destroy();
    String getLastError();
}
