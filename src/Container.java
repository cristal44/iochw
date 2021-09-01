import java.lang.annotation.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/* Finished:
 *  - added interface
 *  - implemented @Qualifier annotation
 *  - provided constructor injection on ServerWithConstructor.class
 */

public class Container {
    private final Map<String, Object> objectFactory = new HashMap<>();
    private final Map<Class<?>, List<String>> interfaceMap = new HashMap<>();

    private List<Class<?>> scan() {
        return Arrays.asList(ServerA.class, ServerB.class, Test.class, ServerWithConstructor.class);
    }

    private boolean register(List<Class<?>> classes) throws Exception{
        // register to interfaceMap
        for (Class<?> implementationClass : classes) {
            Class<?>[] interfaces = implementationClass.getInterfaces();
            if (interfaces.length == 0) {
                interfaceMap.put(implementationClass, Arrays.asList(implementationClass.getSimpleName()));
            } else {
                for (Class<?> f : interfaces) {
                    List<String> list = interfaceMap.containsKey(f) ? interfaceMap.get(f) : new ArrayList<>();
                    list.add(implementationClass.getSimpleName());
                    interfaceMap.put(f, list);
                }
            }
        }

        //  register to objectFactory
        for (Class<?> clazz : classes) {
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation a : annotations) {
                if (a.annotationType() == Component.class) {
                    Constructor<?>[] constructors = clazz.getConstructors();
                    if (constructors.length > 0) {
                        constructorInjectObject(constructors, clazz);
                    } else {
                        objectFactory.put(clazz.getSimpleName(), clazz.getDeclaredConstructor(null).newInstance());
                    }
                }
            }
        }
        return true;
    }

    private void constructorInjectObject(Constructor<?>[] constructors, Class<?> clazz) throws Exception{
        for (Constructor<?> c : constructors) {
            Annotation[] as = c.getAnnotations();
            for (Annotation at : as) {
                if (at.annotationType() == Autowired.class) {
                    Class[] parameterTypes = c.getParameterTypes();
                    List<Object> objects = new ArrayList<>();
                    for (Class cs : parameterTypes) {
                        objects.add(objectFactory.get(cs.getSimpleName()));
                    }
                    Object o = c.newInstance(objects.toArray());
                    objectFactory.put(clazz.getSimpleName(), o);
                }
            }
        }
    }

    private boolean fieldInjectObject(List<Class<?>> classes) throws Exception{
        for (Class<?> clazz : classes) {
            Field[] fields = clazz.getDeclaredFields();
            Object currentObject = objectFactory.get(clazz.getSimpleName());
            for (Field f : fields) {
                Annotation[] annotations = f.getAnnotations();
                for (Annotation a : annotations) {
                    if (a.annotationType() == Autowired.class) {
                        Class<?> c = f.getType();

                        List<String> implementationClasses= interfaceMap.get(c);
                        if (implementationClasses == null || implementationClasses.size() == 0) {
                            f.setAccessible(true);
                            f.set(currentObject, objectFactory.get(c.getSimpleName()));
                        } else if (implementationClasses.size() == 1) {
                            f.setAccessible(true);
                            f.set(currentObject, objectFactory.get(implementationClasses.get(0)));
                        }  else {
                            if (f.isAnnotationPresent(Qualifier.class)) {
                                String qualifier = f.getAnnotation(Qualifier.class).value();
                                if (implementationClasses.contains(qualifier)) {
                                    f.setAccessible(true);
                                    f.set(currentObject, objectFactory.get(qualifier));
                                }
                            } else {
                                throw new Exception("multiple implementations of current type: " + c );
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void start() throws Exception {
        Container container = new Container();
        List<Class<?>> classes = container.scan();
        container.register(classes);
        container.fieldInjectObject(classes);
    }
}

@Component
class Test {
    @Autowired
    @Qualifier("ServerA")
    private static IServer server1;

    @Autowired
    @Qualifier("ServerB")
    private static IServer server2;

    @Autowired
    private static ServerWithConstructor ServerWithConstructor;

    public static void main(String[] args) throws Exception {
        Container.start();
        server1.printData();

        System.out.println("---------");
        server2.printData();

        System.out.println("---------");
        ServerWithConstructor.printData();
    }
}

interface IServer {
    void printData();
}

@Component
class ServerA implements IServer {
    @Override
    public void printData() {
        System.out.println("A");
    }
}

@Component
class ServerB implements IServer {
    @Override
    public void printData() {
        System.out.println("B");
    }
}

@Component
class ServerWithConstructor implements IServer{
    private ServerA serverA;
    private ServerB serverB;

    @Autowired
    public ServerWithConstructor(ServerA serverA, ServerB serverB ) {
        this.serverA = serverA;
        this.serverB = serverB;
    }

    @Override
    public void printData() {
        System.out.println("Data from Servers: ");
        serverA.printData();
        serverB.printData();
    }
}