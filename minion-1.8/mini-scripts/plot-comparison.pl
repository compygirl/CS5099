#! /usr/bin/perl

use Getopt::Long;
use File::Basename;

my $scriptdir = dirname($0);  # $0 = $PROGRAM_NAME

my $dir = ".";
my $help = '';
my $basedir = ".";
my $tmpdir = "/tmp";
my $joinexe = "join"; 
my $sortexe = "sort";
my $uniqexe = "uniq"; 
my $awkexe = "awk"; 
my $gnuplotexe = "gnuplot";
my $egrepexe = "egrep";
my $name1="";
my $name2="";
my $terminal="png";
my $fileext=".png";
my $eps=0;
my $eps_colour=0;

GetOptions( 'outdir=s' => \$dir, 
            'basedir=s' => \$basedir,
            'tmpdir=s' => \$tmpdir,
            'join=s' => \$joinexe,
            'gnuplot=s' => \$gnuplotexe,
            'help' => \$help,
            'name1=s' => \$name1,
            'name2=s' => \$name2,
            'tableout' => \$tableout,
            'eps' => \$eps,
            'eps-colour' => \$eps_colour,
            );

if (@ARGV != 2) 
{
    print "Exactly two directory or file arguments required\n";
}

if($eps || $eps_colour)
{ # alternative output.
    $terminal="postscript eps";
    if($eps_colour) { $terminal .= " colour"; }
    $fileext=".eps";
}

if ($help or (@ARGV != 2))
{
    print << "EOF";
usage: plot-comparison.pl {options}* directory1 directory2
   or: perl plot-comparison.pl ...

options: 
    --help or -h: print this message
    --outdir=DIR or -o DIR: directory for plots to be printed in
        default: .
    --basedir=DIR or -b DIR: directory relative to which directory arguments are understood
        default: .
        note: does not apply to --outdir or --tmpdir
    --tmpdir=DIR or -t DIR: directory to put temporary files in 
        default: .
    --gnuplot=EXECUTABLE or -g EXE: name of gnuplot command
        default: gnuplot
    --join=EXECUTABLE or -j EXE: name of join command
        default: join
    --tableout: Inputs are tableout files rather than plain minion output
EOF
exit;
}

my $newname = $ARGV[0];
my $referencename = $ARGV[1];
my $newdir = "$basedir/$ARGV[0]";
my $referencedir = "$basedir/$ARGV[1]";

if(not $name1 eq "")  # == and != do not work for strings in this ridiculous language.
{
    $newname=$name1;
}
if(not $name2 eq "")
{
    $referencename=$name2;
}

# $^X = perl executable

# join requires input to be in sorted order with -b flag (who knew?)
if($tableout)
{
    # extract columns to approximate the output of analyse-minion.pl. Uses minionversion as a filler for unused columns.
    system "cat $newdir | $scriptdir/getcolumns.py filename minionversion satisfiable solutionsfound totaltime nodes minionversion timeout parsingtime setuptime firstnodetime solvetime minionversion minionversion minionversion | $sortexe -b > $tmpdir/1.$$";
    system "cat $referencedir | $scriptdir/getcolumns.py filename minionversion satisfiable solutionsfound totaltime nodes minionversion timeout parsingtime setuptime firstnodetime solvetime minionversion minionversion minionversion | $sortexe -b > $tmpdir/2.$$";
}
else
{   
    # the old bit which uses analyse_minion.pl to get the values out of minion's stdout 
    if( -f $newdir)
    {
        system "$^X $scriptdir/analyse_minion.pl $newdir | $sortexe -b  > $tmpdir/1.$$";
    }
    else
    {
        system "$^X $scriptdir/analyse_minion.pl $newdir/* | $sortexe -b  > $tmpdir/1.$$";
    }
    
    if( -f $referencedir)
    {
        system "$^X $scriptdir/analyse_minion.pl $referencedir | $sortexe -b > $tmpdir/2.$$";
    }
    else
    {
        system "$^X $scriptdir/analyse_minion.pl $referencedir/* | $sortexe -b > $tmpdir/2.$$";
    }
}

if ((-e $dir) and not (-d $dir) )
{
   print "$dir is not a directory";
   exit;
}
if (! -e $dir) { mkdir($dir) ;} 

system "$joinexe $tmpdir/1.$$ $tmpdir/2.$$ > $dir/comparison.$newname.$referencename.txt";

unlink("$tmpdir/1.$$","$tmpdir/2.$$");        # unlink is rm 


open GNUPLOT1, "| $gnuplotexe " or die "Can't redirect text into gnuplot";
print GNUPLOT1 << "EOF";
set title "Comparison of $newname with $referencename"
set term $terminal
set output "$dir/comparison.$newname.$referencename$fileext"
set log x
set autoscale yfix
set xrange [0.01:*]
set xlabel "run time of $referencename in secs"
set ylabel "%age improvement  in $newname from $referencename "
set grid
set size 1.5,1.2
set pointsize 2
set autoscale yfix
set key outside box 
plot "$dir/comparison.$newname.$referencename.txt" using 19:((((\$6+1)/\$5) / ((\$20+1)/\$19)-1)*100) t "overall nodes/s", \\
     "$dir/comparison.$newname.$referencename.txt" using 19:((((\$6+1)/\$12) / ((\$20+1)/\$26)-1)*100) t "search nodes/s", \\
     "$dir/comparison.$newname.$referencename.txt" using 19:(((\$6+1)/(\$20+1)-1)*100) t "total nodes" 
EOF
close GNUPLOT1;


open GNUPLOT2, ">$tmpdir/$newname.$referencename.$$.gnu";

print GNUPLOT2 << "EOF";
set title "Comparison of $newname with $referencename"
set term $terminal
set output "$dir/breakdown.$newname.$referencename$fileext"
set log x
set log y
set xrange [0.01:*]
set xlabel "run time of $referencename in secs"
set ytics (0.001,0.01,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,0.95,0.99,1,1.01,1.05,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,3,4,5,10,100,1000)
set autoscale yfix
set ylabel "Ratio of nodes/sec $newname to $referencename "
set grid
set size 1.5,1.2
set pointsize 2
set key outside box 
plot 1 not, \\
EOF

my @field;
my $i;


open PREFIXES, "cat $dir/comparison.$newname.$referencename.txt | $awkexe -F '[ 0-9]' '{print \$1}' | $sortexe | $uniqexe |" ;


while (<PREFIXES>) 
{
     @field = split(/\s+/,$_);
     $i = @field[0];
     system "$egrepexe \"^$i\[ 0-9\]\" $dir/comparison.$newname.$referencename.txt > $tmpdir/$newname.$referencename.$$.$i";
     print GNUPLOT2 << "EOF" 
"$tmpdir/$newname.$referencename.$$.$i" using 19:((((\$6+1)/\$5)/ ((\$20+1)/\$19))) t "$i", \\
EOF
}
close PREFIXES;

print GNUPLOT2 "1 not\n" ;

print GNUPLOT2 << "EOF";
set title "Runtime Comparison of $newname with $referencename"
set term $terminal
set output "$dir/timecomparison.$newname.$referencename$fileext"
set log x
set log y
set xrange [0.01:*]
set xlabel "run time of $referencename in secs"
set ytics (0.001,0.01,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,0.95,0.99,1,1.01,1.05,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,3,4,5,10,100,1000)
set autoscale yfix
set ylabel "Ratio of time $referencename to $newname "
set grid
set size 1.5,1.2
set pointsize 2
set key outside box 
plot 1 not, \\
EOF

my @field;
my $i;


open PREFIXES, "cat $dir/comparison.$newname.$referencename.txt | $awkexe -F '[ 0-9]' '{print \$1}' | $sortexe | $uniqexe |" ;


while (<PREFIXES>) 
{
     @field = split(/\s+/,$_);
     $i = @field[0];
     system "$egrepexe \"^$i\[ 0-9\]\" $dir/comparison.$newname.$referencename.txt > $tmpdir/$newname.$referencename.$$.$i";
     print GNUPLOT2 << "EOF" 
"$tmpdir/$newname.$referencename.$$.$i" using 19:((\$19/\$5)) t "$i", \\
EOF
}
close PREFIXES;

print GNUPLOT2 "1 not\n" ;
close GNUPLOT2;

system "$gnuplotexe < $tmpdir/$newname.$referencename.$$.gnu";

system "rm $tmpdir/$newname.$referencename.$$.*" ;

exit;
