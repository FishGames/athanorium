package org.example.resourcemod.core.json;

/**
 * Error in a data file (puzzle, solution, part value). Never crashes the game: loaders catch it, skip the file
 * and log {@link #toString()}, which names file and field.
 */
public class DataException extends RuntimeException {
    private final String file;
    private final String path;

    public DataException(String file, String path, String message) {
        super(message);
        this.file = file;
        this.path = path;
    }

    public String file() {
        return file;
    }

    /** Field path inside the file, e.g. "product.atoms[2].pos". Empty for file-level errors. */
    public String path() {
        return path;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(file);
        if (path != null && !path.isEmpty()) {
            sb.append(" at ").append(path);
        }
        sb.append(": ").append(getMessage());
        return sb.toString();
    }
}
