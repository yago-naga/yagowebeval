package de.mpii.yago.web.evaluation.util;


public class Wilson {
  public static final double TARGET_INTERVAL = 0.05;
  
  public static double[] wilson(int total, int correct) {
    double z = 1.96;
    double p = (double) correct / total;
    double center = (p + 1 / 2.0 / total * z * z)
        / (1 + 1.0 / total * z * z);
    double d = z
        * Math.sqrt((p * (1 - p) + 1 / 4.0 / total * z * z) / total)
        / (1 + 1.0 / total * z * z);
    return (new double[] { center, d });
  }
  
  public static double progress(int total, int correct) {
    double currentInterval = wilson(total, correct)[1];
    
    // 0.05 is the target interval width we are going for
    if (currentInterval < TARGET_INTERVAL) {
      return 1.0;
    } else {
      return 1.0 - (currentInterval - TARGET_INTERVAL);
    }
  }
}
