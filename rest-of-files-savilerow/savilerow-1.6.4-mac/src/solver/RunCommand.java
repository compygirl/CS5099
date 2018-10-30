package savilerow.solver;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import java.util.*;
import java.io.*;
import savilerow.*;

public class RunCommand
{
    // Returns exit code. 
    public static int runCommand(ArrayList<String> command, ArrayList<String> stderr_lines, ReadProcessOutput output_processor) throws IOException,  InterruptedException
    {
        try {
            Process process=Runtime.getRuntime().exec(command.toArray(new String[command.size()]));
            
            InputStream inputStream = process.getInputStream();
            InputStream errorStream = process.getErrorStream();
            BufferedReader input =new BufferedReader(new InputStreamReader(inputStream));
            BufferedReader error =new BufferedReader(new InputStreamReader(errorStream));
            
            ReadProcessOutput rpo2=new ReadProcessOutput(stderr_lines);
            
            output_processor.giveInputStream(input);
            rpo2.giveInputStream(error);
            
            output_processor.start();
            rpo2.start();
            
            output_processor.join();
            rpo2.join();
            
            int exitValue=process.waitFor();
            
            if(stderr_lines.size()!=0 || exitValue!=0) {
                CmdFlags.println("Sub-process exited with error code:"+exitValue+" and error message:");
                CmdFlags.println(stderr_lines);
            }
            return exitValue;
        }
        catch(IOException e1) {
            System.out.println("IOException.");
            throw e1;
        }
        catch(InterruptedException e2) {
            System.out.println("InterruptedException.");
            throw e2;
        }
    }
}
