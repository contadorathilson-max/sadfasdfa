import java.util.*;
import javax.tools.*;
import com.sun.source.util.*;

/** Verifica só a gramática Java sem exigir o Android SDK, não substitui assembleDebug. */
class VerificarSintaxe {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,null);
        Iterable<? extends JavaFileObject> units=manager.getJavaFileObjectsFromStrings(Arrays.asList(args));
        JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,
                Arrays.asList("-proc:none"),null,units);
        int files=0;for(var tree:task.parse())files++;
        for(var problem:diagnostics.getDiagnostics()) {
            if(problem.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(problem);
        }
        System.out.println("Sintaxe Java válida: "+files+" arquivos");
    }
}
