/*
 * Created on 30 juin 2003
 *
 */
package org.gudy.azureus2.ui.swt;

/**
 * @author Olivier
 * 
 */
public interface IComponent {
  
  public void addListener(IComponentListener listener);
  
  public void removeListener(IComponentListener listener);

}
