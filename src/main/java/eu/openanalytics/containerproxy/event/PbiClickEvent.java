package eu.openanalytics.containerproxy.event;

import org.springframework.context.ApplicationEvent;

public class PbiClickEvent extends ApplicationEvent {
   private final String dashboardId;
   private final String userId;

   public PbiClickEvent(Object source, String dashboardId, String userId) {
      super(source);
      this.dashboardId = dashboardId;
      this.userId = userId;
   }

   public String getDashboardId() {
      return this.dashboardId;
   }

   public String getUserId() {
      return this.userId;
   }
}