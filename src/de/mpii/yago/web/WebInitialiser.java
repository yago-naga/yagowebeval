package de.mpii.yago.web;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import mpi.database.DataManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpii.yago.web.evaluation.util.YagoDatabase;

public class WebInitialiser extends HttpServlet {

  private static final long serialVersionUID = 3929122033879136039L;

  private Logger logger;
  
  public WebInitialiser() {
    logger = LoggerFactory.getLogger(WebInitialiser.class);
  }

  @Override
  public void init() throws ServletException {
    super.init();

    try {
      YagoDatabase.connectToDBWithProperties("db_settings.properties");
      
      if (!DataManager.isConnected()) {
        logger.error("FAILED TO CONNECT TO DATABASE");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void destroy() {
    super.destroy();
    try {
      if (DataManager.isConnected()) {
        DataManager.disconnect();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
