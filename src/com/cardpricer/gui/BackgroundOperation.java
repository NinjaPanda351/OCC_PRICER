package com.cardpricer.gui;

import com.cardpricer.service.TaskCoordinator;
import javax.swing.*;
import java.awt.*;

/** Runs a captured local operation off the EDT while its controls cannot be edited. */
public final class BackgroundOperation {
    private BackgroundOperation() {}
    public static void run(Component owner, String title, Runnable operation, Runnable completed) {
        Window window=owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner);
        JDialog progress=new JDialog(window,title,Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progress.add(new JLabel(title+"…",SwingConstants.CENTER));
        progress.setSize(350,100);progress.setLocationRelativeTo(owner);
        SwingWorker<Void,Void> worker=new SwingWorker<>() {
            protected Void doInBackground(){operation.run();return null;}
            protected void done(){
                progress.dispose();
                try{get();completed.run();}
                catch(Exception failure){
                    Throwable cause=failure.getCause()==null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(owner,cause.getMessage(),title+" failed",JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        try {TaskCoordinator.execute(worker);progress.setVisible(true);}
        catch(java.util.concurrent.RejectedExecutionException busy){
            progress.dispose();JOptionPane.showMessageDialog(owner,"Background tasks are busy. Try again shortly.");
        }
    }
}
