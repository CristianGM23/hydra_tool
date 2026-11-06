#!/usr/bin/env python3
"""Compile the actual Langium cardinality emitter, testing exact finite/unbounded ranges."""
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
class LangiumCardinality(unittest.TestCase):
    def test_java_emitter_exact_min_max_and_bounded_rejection(self):
        with tempfile.TemporaryDirectory(prefix='hydra-fix185-langium-') as tmp:
            source=ROOT/'src/main/java/com/example/editor/integration/adapter/langium/LangiumMultiplicityEmitter.java'
            multiplicity=ROOT/'src/main/java/com/example/editor/metamodel/core/Multiplicity.java'
            runner=Path(tmp)/'Test.java'
            runner.write_text('''import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.integration.adapter.langium.LangiumMultiplicityEmitter;
public class Test {
 static void eq(String actual,String expected){if(!actual.equals(expected))throw new AssertionError("expected "+expected+" actual "+actual);}
 public static void main(String[] args){
  String t="[Person:ID]";
  eq(LangiumMultiplicityEmitter.fragment("owner",t,new Multiplicity(1,1))," ('owner' owner="+t+")");
  eq(LangiumMultiplicityEmitter.fragment("owner",t,new Multiplicity(0,1))," ('owner' owner="+t+")?");
  eq(LangiumMultiplicityEmitter.fragment("members",t,new Multiplicity(0,null))," ('members' members+="+t+")*");
  eq(LangiumMultiplicityEmitter.fragment("members",t,new Multiplicity(1,null))," ('members' members+="+t+") ('members' members+="+t+")*");
  eq(LangiumMultiplicityEmitter.fragment("members",t,new Multiplicity(2,3))," ('members' members+="+t+") ('members' members+="+t+") ('members' members+="+t+")?");
  eq(LangiumMultiplicityEmitter.fragment("none",t,new Multiplicity(0,0)),"");
  eq(LangiumMultiplicityEmitter.fragment("default",t,null)," ('default' default="+t+")?");
  for(Multiplicity m :new Multiplicity[]{new Multiplicity(-1,2),new Multiplicity(3,2),new Multiplicity(0,1000)}){
    try{LangiumMultiplicityEmitter.fragment("a",t,m);throw new AssertionError("unsafe range accepted");}catch(IllegalArgumentException expected){}
  }
  System.out.println("FIX185_LANGIUM_MULTIPLICITY=PASS 0..0 0..1 1..1 0..* 1..* 2..3 invalid=BLOCKED");
 }
}''',encoding='utf-8')
            compile=subprocess.run(['javac','--release','21','-d',tmp,str(multiplicity),str(source),str(runner)],timeout=45,capture_output=True,text=True)
            self.assertEqual(compile.returncode,0,compile.stderr)
            run=subprocess.run(['java','-cp',tmp,'Test'],timeout=25,capture_output=True,text=True)
            self.assertEqual(run.returncode,0,run.stdout+'\n'+run.stderr)
            self.assertIn('FIX185_LANGIUM_MULTIPLICITY=PASS',run.stdout)
if __name__=='__main__':unittest.main(verbosity=2)
