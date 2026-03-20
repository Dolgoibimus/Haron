cmake_minimum_required(VERSION 3.22.1)
project("haron_pty")

add_library(haron_pty SHARED pty.c)
target_link_libraries(haron_pty log)
