# This is a http://pythonscad.org/ file
from openscad import *

objs = []

dz = 0.01

cutWidth = 8
cutEyeWidth = 5
cutFoldWidth = 3
step = 10

c = cube([100,140,1])
cutBrush = square([cutWidth, 5 + dz*4])

# smiley
path = [[-(cutWidth+2), 0, -3]]
dir = 1
for i in range(step, 100, step):
    path.append([i,(dir*step) - ((50-i)*(50-i)/100), -3])
    dir*=-1
    # path.append([i+step, step, dz])
    
path.append([100+cutWidth+2,-3])
cut = path_extrude(cutBrush,path)
cutt = cut.translate([0,40,1+dz])

# foldover
cutBrush = square([cutFoldWidth, 5 + dz*4])

foldovergone = cube([50,50,1]).rotate([0,0,45]).translate([0,110,0])

tmpx = 28+(cutFoldWidth+2)
tmpy = 112-(cutFoldWidth+2)
foldover = path_extrude(cutBrush, [[-(cutFoldWidth+2),tmpy,dz], [tmpx, tmpy,dz], [tmpx,140+(cutFoldWidth+2), dz]])

foldover = foldover.translate([0,0,-1-dz])

dash = cube([20,cutEyeWidth,2 + dz*2])

lefteye = dash.rotate([0,0,-15]).translate([15,90,0])
righteye = dash.rotate([0,0,15]).translate([80-15,84.5,0])
# righteye = dash.mirror([1,0,0])


# d = c - cutt - foldovergone - foldover
d = (c - cutt) - foldovergone - foldover - lefteye - righteye

objs.append(d)


prj = d.projection()


output(prj) # | righteye.color("red")) #  | foldover.color("red"))
