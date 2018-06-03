# NetBeansFluentAPICodeGen
A fluent getter/setter API code generator for NetBeans

# Usage
1) Clone repository
2) Build in NetBeans
3) Right-click project and choose "Install/Reload in Development IDE"

# Example
Given the following class:

    class Foo {
      private int anInt;
      private String aString;
    }
  
Running the code generator will generate the following:

    class Foo {
      private int anInt;
      private String aString;

      public int anInt() {
        return this.anInt;
      }

      public Foo anInt(int value) {
        this.anInt = value;
        return this;
      }

      public String aString() {
        return this.aString;
      }

      public Foo aString(String value) {
        this.aString = value;
        return this;
      }
    }
  
