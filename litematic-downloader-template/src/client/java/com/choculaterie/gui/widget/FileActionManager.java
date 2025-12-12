package com.choculaterie.gui.widget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages undo/redo operations for file actions
 */
public class FileActionManager {
    
    public enum ActionType { MOVE, DELETE, RENAME, CREATE_FOLDER }
    
    /**
     * Represents a single file operation (source -> destination)
     */
    public static class FileOperation {
        public final File source;
        public final File destination;
        public final boolean wasDirectory;

        public FileOperation(File source, File destination, boolean wasDirectory) {
            this.source = source;
            this.destination = destination;
            this.wasDirectory = wasDirectory;
        }
    }
    
    /**
     * Represents a file action that can be undone/redone
     */
    public static class FileAction {
        public final ActionType type;
        public final List<FileOperation> operations;

        public FileAction(ActionType type, List<FileOperation> operations) {
            this.type = type;
            this.operations = operations;
        }

        public FileAction(ActionType type, FileOperation operation) {
            this.type = type;
            this.operations = new ArrayList<>();
            this.operations.add(operation);
        }
    }
    
    private final List<FileAction> undoStack = new ArrayList<>();
    private final List<FileAction> redoStack = new ArrayList<>();
    private final int maxHistory;
    private final File trashFolder;
    
    public FileActionManager(File trashFolder, int maxHistory) {
        this.trashFolder = trashFolder;
        this.maxHistory = maxHistory;
        
        if (!trashFolder.exists()) {
            trashFolder.mkdirs();
        }
    }
    
    public void addAction(FileAction action) {
        undoStack.add(action);
        redoStack.clear(); // Clear redo stack when new action is performed
        
        // Limit undo history
        while (undoStack.size() > maxHistory) {
            FileAction removed = undoStack.removeFirst();
            cleanupAction(removed);
        }
    }
    
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    /**
     * Performs undo and returns a description of what was undone, or null if nothing to undo
     */
    public String performUndo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        
        FileAction action = undoStack.removeLast();
        String result = null;
        
        switch (action.type) {
            case MOVE:
                result = undoMove(action);
                break;
            case DELETE:
                result = undoDelete(action);
                break;
            case RENAME:
                result = undoRename(action);
                break;
            case CREATE_FOLDER:
                result = undoCreateFolder(action);
                break;
        }
        
        if (result != null) {
            // Limit redo history
            while (redoStack.size() >= maxHistory) {
                redoStack.removeFirst();
            }
            redoStack.add(action);
        }
        
        return result;
    }
    
    /**
     * Performs redo and returns a description of what was redone, or null if nothing to redo
     */
    public String performRedo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        
        FileAction action = redoStack.removeLast();
        String result = null;
        
        switch (action.type) {
            case MOVE:
                result = redoMove(action);
                break;
            case DELETE:
                result = redoDelete(action);
                break;
            case RENAME:
                result = redoRename(action);
                break;
            case CREATE_FOLDER:
                result = redoCreateFolder(action);
                break;
        }
        
        if (result != null) {
            // Limit undo history
            while (undoStack.size() >= maxHistory) {
                FileAction removed = undoStack.removeFirst();
                cleanupAction(removed);
            }
            undoStack.add(action);
        }
        
        return result;
    }
    
    private String undoMove(FileAction action) {
        int successCount = 0;
        for (FileOperation op : action.operations) {
            if (op.destination.exists() && !op.source.exists()) {
                if (op.destination.renameTo(op.source)) {
                    successCount++;
                }
            }
        }
        if (successCount > 0) {
            return "Undid move of " + successCount + " item(s)";
        }
        return null;
    }
    
    private String undoDelete(FileAction action) {
        int successCount = 0;
        for (FileOperation op : action.operations) {
            // Restore from trash (destination) to original location (source)
            if (op.destination.exists() && !op.source.exists()) {
                if (op.destination.renameTo(op.source)) {
                    successCount++;
                }
            }
        }
        if (successCount > 0) {
            return "Restored " + successCount + " item(s)";
        }
        return null;
    }
    
    private String undoRename(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists() && !op.source.exists()) {
            if (op.destination.renameTo(op.source)) {
                return "Undid rename of \"" + op.destination.getName() + "\"";
            }
        }
        return null;
    }
    
    private String undoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists() && op.destination.isDirectory()) {
            File[] contents = op.destination.listFiles();
            if (contents == null || contents.length == 0) {
                if (op.destination.delete()) {
                    return "Undid creation of \"" + op.destination.getName() + "\"";
                }
            }
        }
        return null;
    }
    
    private String redoMove(FileAction action) {
        int successCount = 0;
        for (FileOperation op : action.operations) {
            if (op.source.exists() && !op.destination.exists()) {
                if (op.source.renameTo(op.destination)) {
                    successCount++;
                }
            }
        }
        if (successCount > 0) {
            return "Redid move of " + successCount + " item(s)";
        }
        return null;
    }
    
    private String redoDelete(FileAction action) {
        int successCount = 0;
        for (FileOperation op : action.operations) {
            // Move from original location (source) back to trash (destination)
            if (op.source.exists() && !op.destination.exists()) {
                if (op.source.renameTo(op.destination)) {
                    successCount++;
                }
            }
        }
        if (successCount > 0) {
            return "Deleted " + successCount + " item(s) again";
        }
        return null;
    }
    
    private String redoRename(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();
        if (op.source.exists() && !op.destination.exists()) {
            if (op.source.renameTo(op.destination)) {
                return "Redid rename to \"" + op.destination.getName() + "\"";
            }
        }
        return null;
    }
    
    private String redoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();
        if (!op.destination.exists()) {
            if (op.destination.mkdir()) {
                return "Recreated folder \"" + op.destination.getName() + "\"";
            }
        }
        return null;
    }
    
    /**
     * Clean up files in trash that are no longer needed for undo
     */
    private void cleanupAction(FileAction action) {
        if (action.type == ActionType.DELETE) {
            for (FileOperation op : action.operations) {
                if (op.destination.exists() && op.destination.getAbsolutePath().contains(".trash")) {
                    deleteRecursively(op.destination);
                }
            }
        }
    }
    
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    public String getUndoDescription() {
        if (undoStack.isEmpty()) return null;
        FileAction action = undoStack.getLast();
        return getActionDescription(action, "Undo");
    }
    
    public String getRedoDescription() {
        if (redoStack.isEmpty()) return null;
        FileAction action = redoStack.getLast();
        return getActionDescription(action, "Redo");
    }
    
    private String getActionDescription(FileAction action, String prefix) {
        switch (action.type) {
            case MOVE:
                return prefix + " move";
            case DELETE:
                return prefix + " delete";
            case RENAME:
                if (!action.operations.isEmpty()) {
                    return prefix + " rename of \"" + action.operations.getFirst().source.getName() + "\"";
                }
                return prefix + " rename";
            case CREATE_FOLDER:
                if (!action.operations.isEmpty()) {
                    return prefix + " creation of \"" + action.operations.getFirst().destination.getName() + "\"";
                }
                return prefix + " folder creation";
            default:
                return prefix;
        }
    }
}

