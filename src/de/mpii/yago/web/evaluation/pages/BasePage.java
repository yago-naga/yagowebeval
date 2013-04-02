package de.mpii.yago.web.evaluation.pages;

import java.text.DecimalFormat;
import java.util.Locale;

import org.apache.click.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasePage extends Page {

  private Logger logger;
   
  protected DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
  
  public BasePage() {
    nf.applyPattern("#.##");
  }
  
  public Logger getLogger() {
      if (logger == null) {
          logger = LoggerFactory.getLogger(getClass());
      }
      return logger;
  }
  
  public String getTemplate() {
    return "/general-template.htm";
  }

}
