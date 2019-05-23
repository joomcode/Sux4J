/*
 * Sux: Succinct data structures
 *
 * Copyright (C) 2018 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

#include <stdio.h>
#include <inttypes.h>
#include <fcntl.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>

static uint64_t get_system_time(void) {
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return tv.tv_sec * 1000000 + tv.tv_usec;
}

int main(int argc, char* argv[]) {
	int h = open(argv[1], O_RDONLY);
	assert(h >= 0);
	SUX4J_MAP *SUX4J_MAP = SUX4J_LOAD_MAP(h);
	close(h);

#define NKEYS 10000000
	h = open(argv[2], O_RDONLY);
	off_t len = lseek(h, 0, SEEK_END);
	lseek(h, 0, SEEK_SET);
	char *data = malloc(len);
	read(h, data, len);
	close(h);
	
	static char *test_buf[NKEYS];
	static int test_len[NKEYS];
	
	char *p = data;
	for(int i = 0; i < NKEYS; i++) {
		while(*p == 0xA || *p == 0xD) p++;
		test_buf[i] = p;
		while(*p != 0xA && *p != 0xD) p++;
		test_len[i] = p - test_buf[i];
	}

	uint64_t total = 0;
	uint64_t u = 0;

	for(int k = 10; k-- != 0; ) {
		int64_t elapsed = - get_system_time();
		for (int i = 0; i < NKEYS; ++i) u += SUX4J_GET_BYTE_ARRAY(SUX4J_MAP, test_buf[i], test_len[i]);

		elapsed += get_system_time();
		total += elapsed;
		printf("Elapsed: %.3fs; %.3f ns/key\n", elapsed * 1E-6, elapsed * 1000. / NKEYS);
	}
	const volatile int unused = u;
	printf("\nAverage: %.3fs; %.3f ns/key\n", (total * .1) * 1E-6, (total * .1) * 1000. / NKEYS);
}
