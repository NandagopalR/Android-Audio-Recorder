import numpy as np
import matplotlib.pyplot as plt
import scipy.fftpack
import random

#
# https://web.archive.org/web/20120615002031/http://www.mathworks.com/support/tech-notes/1700/1702.html
#

def noise(y, amp):
  return y + amp * np.random.sample(len(y))

# Fe = sample rate
# N = samples count
def plot(Fe, N, x, y):
  plt.subplot(2, 1, 1)
  print "power wav = %s" % np.sqrt(np.mean(y**2))
  plt.plot(x, y)

  plt.subplot(2, 1, 2)
  yf = scipy.fftpack.fft(y)
  
  NumUniquePts = np.ceil((N+1)/2)
  # Bin 0 contains the value for the DC component that the signal is riding on.
  fftx = yf[1:NumUniquePts]
  mx = np.abs(fftx)
  mx = mx / N
  mx = mx**2
  if N % 2 > 0:
    mx[2:len(mx)] = mx[2:len(mx)]*2
  else:
    mx[2:len(mx)-1] = mx[2:len(mx)-1]*2
  print "power fft = %s" % np.sqrt(np.sum(mx))

  end = Fe/2
  start = end / (N/2)
  xf = np.linspace(start, end, N/2 - 1)
  mx = np.sqrt(mx)
  plt.plot(xf, mx)
  
  plt.show()

def simple(Fe):
  N = Fe
  x = np.linspace(0.0, 1.0, N)

  y = np.zeros(len(x))

  y += 0.9 * np.sin(50.0 * 2.0*np.pi*x) + 0.5*np.sin(80.0 * 2.0*np.pi*x)
  y += 0.6 * np.sin(20.0 * 2.0*np.pi*x) + 0.5*np.sin(80.0 * 2.0*np.pi*x)
  y += 0.2 * np.sin(80.0 * 2.0*np.pi*x) + 0.5*np.sin(80.0 * 2.0*np.pi*x)
  
  #y = noise(y, 2)

  plot(Fe, N, x, y)

def real_sound_weave(durationMs):
  Fe = 16000
  N = Fe * durationMs / 1000
  x = np.linspace(0.0, N, N)
  
  y = np.zeros(len(x))
  
  wav_max = 0x7fff
  
  y += np.sin(2.0 * np.pi * x / (Fe / float(4500))) * wav_max
  y += 0.5 * np.sin(2.0 * np.pi * x / (Fe / float(4000))) * wav_max
  y += 0.5 * np.sin(2.0 * np.pi * x / (Fe / float(1000))) * wav_max
  y += 0.9 * np.sin(2.0 * np.pi * x / (Fe / float(7500))) * wav_max
  y += 1 * np.sin(2.0 * np.pi * x / (Fe / float(3000))) * wav_max

  y = y / np.max(np.abs(y)) * wav_max

  #y = noise(y, 0x7fff)

  plot(Fe, N, x, y)

#simple(1000)
real_sound_weave(100)