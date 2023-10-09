
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class BashMagics {

   @LineMagic(aliases={"bash","shell"})
   public void bash(List<String> args) {
      ProcessBuilder pb = new ProcessBuilder(args);
      StringBuffer sb = new StringBuffer();
      try {
         Process p = pb.start();
         BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
         p.waitFor();
         String cad = "";
         while ((cad = br.readLine()) != null){
            sb.append(cad);
            sb.append('\n');
         }
         br.close();
      } catch (Exception ex) {
         ex.printStackTrace();
      }
      System.out.println(sb.toString());
   }
}
