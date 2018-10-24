#! /usr/bin/perl

#Script written by Ian Gent, 10/10/2006, based on ... 
#Script written by Ian Gent, 26/8/99

#use Descriptive;

use File::Basename;


if ((@ARGV < 1) or ($ARGV[0] =~ /^--help$/)) 
{ 
  print "usage: analyse_minion.pl {--headers} filename*\n" ;
  print "or:    perl analyse_minion.pl ...\n" ;
  print "    analyses minion results in the given filenames,\n";
  print "    gives out one line of data per result\n";
  print " --headers (optional) prints out one word summary of columns in order\n";
  
  exit;
}


FILELOOP:
for($arg=0;$arg < @ARGV; $arg++) {
  
  if (($arg==0) and ($ARGV[$arg] =~ /^--headers$/)) {print_headers();}
  else 
      {

      $filename = $ARGV[$arg];

      $res = "";

      open(FILENAME,$filename)
       || die "can't open $filename/n";

      # $filename =~ /([0-9]*)\./ ;
      # my $id = $1;
      $resbin = 0 ; 

      while (defined($line = <FILENAME>))
            {
              
              $line =~ s/^\s*//;
              @field = split(/\s+/,$line);
              next FILELOOP if ($line =~ /Segmentation\s*fault/);
              if ($line =~ /^# Minion Version/) 
              { 
                  print_line();    

              # initialisation for this instance
                  $resbin = 0;
                  $res = "";
                  $timeout = "Completed";
		  $git_version = "0";
                  $version = $field[3] ;
                  @parsing_time = -1;
                  @setup_time = -1;
                  @first_node_time = -1;
                  @first_node_time2 = -1;
                  @initial_propagate = -1;
                  $solve_time = 0;
                  $total_time = 0;
                  $wall_time = -1;
                  @bytes_used = -1;
                  @total_nodes = -1;
                  @num_solutions = -1;
              } 
              if ($line =~ /^# Input filename/) 
              { $instance_fullname =  $field[3] ; 
                $instance_name = fileparse($instance_fullname) ;
              }
              if ($line =~ /^Time out/) { $timeout = "Timeout"; } 
              if ($line =~ /^Problem solvable.*yes/) 
              { 
                  $res = "Solvable" ;
                  $resbin = 1;
              } 
              if ($line =~ /^Problem solvable.*no/) 
              { 
                  $res = "Unsolvable" ;
                  $resbin = 0;
              } 
              #if ($line =~ /^Parsing Time=/) { @heuristic =  split(/,/,$field[2]); }
              if ($line =~ /^# Git version/) { $git_version = $field[3] } 
              if ($line =~ /^Parsing Time/) { @parsing_time =  $field[2] ; }
              if ($line =~ /^Setup Time/) { @setup_time =  $field[2] ; }
              if ($line =~ /^First node time/) { @first_node_time =  $field[3] ; }
              if ($line =~ /^First Node Time/) { @first_node_time2 =  $field[3] ; }
              if ($line =~ /^Initial Propagate/) { @initial_propagate =  $field[2] ; }
              if ($line =~ /^Solve Time/) { $solve_time =  $field[2] ; }
              if ($line =~ /^Total Time/) { $total_time =  $field[2] ; }
              if ($line =~ /^Total Wall Time/) { $wall_time =  $field[3] ; }
              if ($line =~ /^Maximum Memory/) { @bytes_used = $field[3] ; }
              if ($line =~ /^Total Nodes/) { $total_nodes =  $field[2]; }
              if ($line =~ /^Solutions Found/) { $num_solutions =  $field[2]; }
            }

      close(FILENAME);
      print_line();
      }
  }

exit;


sub print_line
{
 
  my $nodesper, $nodesper_solving;
  $nodesper = 0 if ($total_time == 0);
  $nodesper_solving = 0 if ($solve_time ==0);
  $nodesper = ($total_nodes/$total_time) unless ($total_time==0);
  $nodesper_solving = ($total_nodes/$solve_time) unless ($solve_time==0);

  #if changing this remember to update print_headers!

  print "$instance_name $version $git_version $res $num_solutions $total_time $total_nodes $nodesper $timeout @parsing_time @setup_time @first_node_time @first_node_time2 @initial_propagate $solve_time $wall_time $nodesper_solving @bytes_used $filename \n" 
    if (!($res eq ""));
}  

sub print_headers
{
  print "instance_filename minion_version git_version solvable_or_unsolvable num_solutions total_time total_nodes nodes_per_sec completed_or_timeout parsing_time setup_time first_node_time First_Node_time initial_propagate_time solve_time wall_time nodes_per_sec_solving max_memory_kb data_filename\n" ;
}  
