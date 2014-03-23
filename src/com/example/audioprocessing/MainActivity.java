package com.example.audioprocessing;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class MainActivity extends Activity
{
	private static final int RECORDER_SAMPLERATE = 8000;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	private AudioRecord recorder = null;
	private int bufferSize = 0;
	private boolean isRecording = false;
	private Thread processingThread = null;
	private int DetectAfterEvery = 0;
	private float DetectSec = 1;
	private short[] audioData = null;
	private int FFcount = 0;
	private int HFcount = 0;
	private int HAPRcount = 0;
	private boolean firstTime = true;
	private short[] beforeData = null;
	private int HF_N = 5;
	private int HAPR_M = 5;
	public int answer = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		try
		{
			setButtonHandlers();
			enableButtons(false);

			bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
					RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

			DetectAfterEvery = (int) ((float) RECORDER_SAMPLERATE * DetectSec);

			// if (DetectAfterEvery > bufferSize)
			// {
			// AppLog.logString("Increasing buffer to hold enough samples "
			// + DetectAfterEvery + " was: " + bufferSize);
			// bufferSize = DetectAfterEvery;
			// }
			// bufferSize = 84376;
			audioData = new short[bufferSize];
			beforeData = new short[bufferSize];
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void setButtonHandlers()
	{
		((Button) findViewById(R.id.start)).setOnClickListener(btnClick);
		((Button) findViewById(R.id.stop)).setOnClickListener(btnClick);

	}

	private void enableButton(int id, boolean isEnable)
	{
		((Button) findViewById(id)).setEnabled(isEnable);
	}

	private void enableButtons(boolean isRecording)
	{
		enableButton(R.id.start, !isRecording);
		enableButton(R.id.stop, isRecording);
	}

	private void startRecording()
	{
		recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
				RECORDER_SAMPLERATE, RECORDER_CHANNELS,
				RECORDER_AUDIO_ENCODING, bufferSize);

		recorder.setPositionNotificationPeriod(DetectAfterEvery);
		recorder.setRecordPositionUpdateListener(positionUpdater);

		isRecording = true;

		processingThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				recorder.startRecording();
				while (isRecording)
				{
					int read = recorder.read(audioData, 0, bufferSize);
					if (firstTime)
					{
						firstTime = false;
						for (int i = 0; i < read; i++)
						{
							beforeData[i] = audioData[i];
						}
					} else
					{
						for (int i = 0; i < read; i++)
						{
							// first step:low before data = high audio data
							beforeData[i] = audioData[(read / 2) + i];
						}
						for (int i = 0; i < read; i++)
						{
							// second step:high audio data = low audio data
							audioData[(read / 2) + i] = audioData[i];
						}
						for (int i = 0; i < read; i++)
						{
							// third step:low audio data = high before data
							audioData[i] = beforeData[(read / 2) + i];
						}
						for (int i = 0; i < read; i++)
						{
							// fourth step:high before data = before before data
							beforeData[(read / 2) + i] = beforeData[i];
						}
					}
					// // short-time energy
					// double[] temp = new double[read];
					// for (int i = 0; i < temp.length; i++)
					// {
					// temp[i] = 0;
					// }
					// for (int i = 0; i < temp.length; i++)
					// {
					// temp[i] = 0;
					// for (int j = 0; j < read; j++)
					// {
					// temp[i] = temp[i] + audioData[j] * audioData[j];
					// }
					// temp[i] = temp[i] / read;
					// }
					// double[] doubleAudioData = new double[read];
					// for (int i = 0; i < read; i++)
					// {
					// doubleAudioData[i] = temp[i];
					// }
					DoubleFFT_1D fft = new DoubleFFT_1D(read);
					double[] a = new double[read * 2];
					for (int i = 0; i < read; i++)
					{
						a[2 * i] = (double) audioData[i];
						a[2 * i + 1] = 0;
					}
					fft.complexForward(a);
					try
					{
						int max_i = -1;
						double asp = 0;
						double HAPR = 0;
						double[][] Hi = new double[HF_N][3];
						for (int i = 0; i < HF_N; i++)
						{
							Hi[i][0] = -1;
							Hi[i][1] = -1;
							Hi[i][2] = -1;
						}
						double max_fftval = -1;
						for (int i = 0; i < a.length; i += 2)
						{
							// we are only looking at the half of the spectrum
							// double hz = (i / a.length) * RECORDER_SAMPLERATE;
							// AppLog.logString("i: " + i + ", HZ: " + hz);
							// asp = asp + hz * hz;
							// AppLog.logString(i
							// + ".\tr:"
							// + Double.toString((Math.abs(a[i]) > 0.1 ? a[i]
							// : 0))
							// + " i:"
							// + Double.toString((Math.abs(a[i + 1]) > 0.1 ? a[i
							// + 1]
							// : 0)) + ", " + hz + "hz\n");
							a[i] = (Math.abs(a[i]) > 0.1 ? a[i] : 0);
							a[i + 1] = (Math.abs(a[i + 1]) > 0.1 ? a[i + 1] : 0);
							// complex numbers -> vectors, so we compute the
							// length of the vector, which is
							// sqrt(realpart^2+imaginarypart^2)
							double vlen = Math.sqrt(a[i] * a[i] + a[i + 1]
									* a[i + 1]);
							// AppLog.logString("i: " + i + ", vlen: " + vlen
							// + ", Freq: " + ((double) i / a.length)
							// * RECORDER_SAMPLERATE);
							asp = asp + vlen * vlen;
							for (int j = 0; j < HF_N; j++)
							{
								if (Hi[j][1] < vlen)
								{
									Hi[j][0] = i;
									Hi[j][1] = vlen;
									Hi[j][2] = ((double) i / a.length)
											* RECORDER_SAMPLERATE;
									if ((((double) i / a.length) * RECORDER_SAMPLERATE) > (RECORDER_SAMPLERATE / 2))
									{
										Hi[j][2] = RECORDER_SAMPLERATE
												- ((double) i / a.length)
												* RECORDER_SAMPLERATE;
									}
									break;
								}
							}
							if (max_fftval < vlen)
							{
								// if this length is bigger than our stored
								// biggest length
								max_fftval = vlen;
								max_i = i;
							}
						}
						if (max_fftval < 50000)
						{
							continue;
						}
						// FF
						double dominantFreq = ((double) max_i / a.length)
								* RECORDER_SAMPLERATE;
						if ((((double) max_i / a.length) * RECORDER_SAMPLERATE) > (RECORDER_SAMPLERATE / 2))
						{
							dominantFreq = RECORDER_SAMPLERATE
									- ((double) max_i / a.length)
									* RECORDER_SAMPLERATE;
						}
						if (dominantFreq > 300 && dominantFreq < 600)
						{
							FFcount++;
						}
						if (dominantFreq <= 0)
						{
							continue;
						}
						// HF
						double HF = 0;
						for (int i = 0; i < HF_N; i++)
						{

							if (Hi[i][0] != -1)
							{
								if ((dominantFreq - (Hi[i][2] % dominantFreq)) < (Hi[i][2] % dominantFreq))
								{
									 AppLog.logString("Hi: "
									 + Hi[i][0]
									 + ", Hvlen: "
									 + Hi[i][1]
									 + ", HFreq: "
									 + Hi[i][2]
									 + ", HF: "
									 + (dominantFreq - (Hi[i][2] %
									 dominantFreq)));
									HF = HF
											+ (dominantFreq - (Hi[i][2] % dominantFreq));
								} else
								{
									 AppLog.logString("Hi: " + Hi[i][0]
									 + ", Hvlen: " + Hi[i][1]
									 + ", HFreq: " + Hi[i][2] + ", HF: "
									 + (Hi[i][2] % dominantFreq));
									HF = HF + (Hi[i][2] % dominantFreq);
								}
							}
						}
						if (HF < 200 && HF > 0)
						{
							HFcount++;
						}
						// HAPR
						double value = 0;
						asp = asp / (a.length / 2.0);
						for (int i = 2; i < HAPR_M; i++)
						{
							int number = (int) ((dominantFreq / (double) (RECORDER_SAMPLERATE * i)) * a.length);
//							AppLog.logString("i: " + number);
							value = value + a[number] * a[number]
									+ a[number + 1] * a[number + 1];
							
						}
						HAPR = 10 * Math.log10(value / asp);
						// AppLog.logString("HAPR" + HAPR + ", ASP" + asp);
						HAPR = HAPR / HAPR_M;
						if (HAPR > 10 && HAPR < 30)
						{
							HAPRcount++;
						}
						AppLog.logString("frequency: " + dominantFreq
								+ "hz, HF: " + HF + ", HAPR: " + HAPR);

					} catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}, "AudioProcessing Thread");

		processingThread.start();
	}

	private void stopRecording()
	{
		if (null != recorder)
		{
			isRecording = false;

			recorder.stop();
			recorder.release();

			recorder = null;
			processingThread = null;

			firstTime = true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public AudioRecord.OnRecordPositionUpdateListener positionUpdater = new AudioRecord.OnRecordPositionUpdateListener()
	{
		@Override
		public void onPeriodicNotification(AudioRecord recorder)
		{
			// recorder.read(audioData, 0, bufferSize);

			// do something amazing with audio data
			AppLog.logString("periodic reached, FF: " + FFcount + ", HF: "
					+ HFcount + ", HAPR: " + HAPRcount);
			if (FFcount >= 5)
			{
				answer++;
			}
			if (HFcount >= 5)
			{
				answer++;
			}
			if (HAPRcount > RECORDER_SAMPLERATE / bufferSize)
			{
				answer++;
			}
			if (answer >= 2)
			{
				((TextView) findViewById(R.id.txt)).setText("cry");
			} else
			{
				((TextView) findViewById(R.id.txt)).setText("");
			}
			FFcount = 0;
			HFcount = 0;
			HAPRcount = 0;
			answer = 0;
		}

		@Override
		public void onMarkerReached(AudioRecord recorder)
		{
			AppLog.logString("marker reached");
		}
	};

	private View.OnClickListener btnClick = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			switch (v.getId())
			{
			case R.id.start:
			{
				AppLog.logString("Start Recording");

				enableButtons(true);
				startRecording();

				break;
			}
			case R.id.stop:
			{
				AppLog.logString("Stop Recording");

				enableButtons(false);
				stopRecording();

				break;
			}
			}
		}
	};
}
