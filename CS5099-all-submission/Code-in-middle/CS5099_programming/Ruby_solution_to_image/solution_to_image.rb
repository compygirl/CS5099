solution = File.read("./solution")
solution = solution[solution.index("solutionBoard be")+17..solution.index("letting topIndexMatrix be")-2]
solution.gsub!(/;int\(.+?\)/, '')
solution = eval(solution)
puts '_'*solution[0].length*3
solution.each_with_index do |row, n|
	row.each_with_index do |x, i|
		print (i % 5 == 0) ? '│' : ((n+1) % 5 == 0) ? '_' : ' '
		print x == 1 ? '⏹' : ((n+1) % 5 == 0) ? '_' : ' '
		print ((n+1) % 5 == 0) ? '_' : ' '
	end
	print "|\n"
end

