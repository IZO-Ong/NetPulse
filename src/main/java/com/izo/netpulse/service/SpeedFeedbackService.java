package com.izo.netpulse.service;

import org.springframework.stereotype.Service;

@Service
public class SpeedFeedbackService {
    public String getFeedback(double dl) {
        if (dl >= 200) return "Your Internet connection is very fast. It handles 4K streaming and gaming on multiple devices.";
        if (dl >= 150) return "Your connection is fast. Great for high-quality streaming and smooth gaming simultaneously.";
        if (dl >= 100) return "Good connection. Sufficient for HD streaming and standard online activities for a small household.";
        if (dl >= 50)  return "Basic connection. Suitable for single-device HD streaming and general web browsing.";
        return "Slow connection. You may experience buffering during HD playback or lag during online gaming.";
    }
}