package tw.org.il.dongsheng.templeapp.update;

import java.net.URI;

public record AppUpdate(String version, URI downloadUri, String fileName) {
}
