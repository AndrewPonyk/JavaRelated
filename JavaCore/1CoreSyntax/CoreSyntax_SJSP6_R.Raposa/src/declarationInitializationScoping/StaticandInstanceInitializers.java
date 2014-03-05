package declarationInitializationScoping;
public class StaticandInstanceInitializers{
    public StaticandInstanceInitializers(){
        System.out.println("Constructor");
    }
    {
        System.out.println("instance initializer");
    }
    
    {
        System.out.println("instance initializer2");
    }
    
    static {
        System.out.println("Static initializer1");
    }
    
    static {
        System.out.println("Static initializer2");
    }
    

     public static void main(String []args){
        System.out.println("Hello World");
        
        StaticandInstanceInitializers w =new StaticandInstanceInitializers();
     }
}
